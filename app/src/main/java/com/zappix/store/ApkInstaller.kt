package com.zappix.store

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Everything needed to download one APK and prove it is the right one. */
data class ApkDownloadRequest(
    val appId: Int,
    val displayName: String,
    val url: String,
    val expectedPackage: String,
    /** The downloaded APK must have at least this versionCode (null = no requirement). */
    val minVersionCode: Long?,
    val sha256: String?
)

data class PreparedApk(
    val file: File,
    val packageName: String,
    val versionCode: Long
)

class ApkInstaller(context: Context) {
    private val appContext = context.applicationContext
    private val pm: PackageManager get() = appContext.packageManager
    private val apkDir = File(appContext.cacheDir, "apks")

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || pm.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }

    /** Deletes cached APKs, except [keep] (an APK still waiting to be handed to the installer). */
    fun cleanup(keep: File? = null) {
        apkDir.listFiles()?.forEach { if (it != keep) it.delete() }
    }

    /**
     * Downloads and fully verifies an APK. Throws [UserFacingException] (or an IO error) on any
     * problem; a partially written or rejected file is always deleted.
     */
    suspend fun download(
        request: ApkDownloadRequest,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): PreparedApk = withContext(Dispatchers.IO) {
        val expectedPackage = request.expectedPackage.trim()
        if (!isValidPackageName(expectedPackage)) {
            throw ApkRejectedException(
                "${request.displayName} has no valid package name in the Zappix catalog, so it can't be verified."
            )
        }
        val startUrl = request.url.trim().toHttpUrlOrNull()
            ?: throw ApkRejectedException("The download link for ${request.displayName} is invalid.")
        checkScheme(startUrl, previous = null)

        apkDir.mkdirs()
        val safeName = request.displayName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "app" }
        val file = File(apkDir, "$safeName-${request.appId}-${System.currentTimeMillis()}.apk")

        var ok = false
        try {
            val (finalUrl, digestHex) = fetch(startUrl, file, onProgress)
            val prepared = verify(file, request, expectedPackage, digestHex, finalUrl)
            ok = true
            prepared
        } finally {
            if (!ok) file.delete()
        }
    }

    private fun checkScheme(url: HttpUrl, previous: HttpUrl?) {
        if (previous != null && previous.isHttps && !url.isHttps) {
            throw UserFacingException("Download blocked: the server tried to switch to an insecure (http) link.")
        }
        if (!url.isHttps && !BuildConfig.ALLOW_HTTP_DOWNLOADS) {
            throw ApkRejectedException("Download blocked: this app's download link is not secure (https).")
        }
    }

    /** Streams the file to disk, following up to 8 validated redirects. Returns final URL + SHA-256. */
    private suspend fun fetch(
        startUrl: HttpUrl,
        file: File,
        onProgress: (Long, Long) -> Unit
    ): Pair<HttpUrl, String> {
        var url = startUrl
        var redirects = 0
        while (true) {
            val httpRequest = Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache, no-store")
                .header("Pragma", "no-cache")
                .build()
            val response = Net.download.newCall(httpRequest).await()
            response.use { res ->
                if (res.isRedirect) {
                    val next = res.header("Location")?.let { url.resolve(it) }
                        ?: throw UserFacingException("Download failed: the server sent a broken redirect.")
                    checkScheme(next, previous = url)
                    if (++redirects > 8) throw UserFacingException("Download failed: too many redirects.")
                    url = next
                    return@use
                }
                if (!res.isSuccessful) {
                    throw UserFacingException(
                        when (res.code) {
                            404 -> "Download failed: the file was not found on the server (404)."
                            401, 403 -> "Download failed: the server refused access (${res.code})."
                            else -> "Download failed: server error (${res.code}). Please try again later."
                        }
                    )
                }
                val body = res.body ?: throw UserFacingException("Download failed: the server sent an empty file.")
                if (body.contentType()?.subtype?.equals("html", ignoreCase = true) == true) {
                    throw UserFacingException("Download failed: the server sent a web page instead of an app. The download link may be broken.")
                }
                val total = body.contentLength()
                ensureFreeSpace(total)

                val digest = MessageDigest.getInstance("SHA-256")
                var copied = 0L
                var lastPercent = -1
                var lastReportedBytes = 0L
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            copied += read
                            if (total > 0) {
                                val percent = ((copied * 100) / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(copied, total)
                                }
                            } else if (copied - lastReportedBytes >= 512 * 1024) {
                                lastReportedBytes = copied
                                onProgress(copied, total)
                            }
                        }
                    }
                }
                if (total > 0 && copied != total) {
                    throw UserFacingException("The download was interrupted. Please try again.")
                }
                return url to digest.digest().joinToString("") { "%02x".format(it) }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException("unreachable")
    }

    private fun ensureFreeSpace(total: Long) {
        if (total <= 0) return
        val available = try {
            StatFs(apkDir.path).availableBytes
        } catch (_: Exception) {
            return
        }
        // The system installer needs roughly another copy of the APK while installing.
        val needed = total * 2 + 50L * 1024 * 1024
        if (available < needed) {
            val missingMb = (needed - available) / (1024 * 1024) + 1
            throw UserFacingException("Not enough free storage on this TV. Free up about $missingMb MB and try again.")
        }
    }

    private fun verify(
        file: File,
        request: ApkDownloadRequest,
        expectedPackage: String,
        digestHex: String,
        finalUrl: HttpUrl
    ): PreparedApk {
        request.sha256?.let { expected ->
            if (!expected.equals(digestHex, ignoreCase = true)) {
                Log.w(TAG, "Checksum mismatch for ${request.displayName}: expected $expected got $digestHex from $finalUrl")
                throw UserFacingException("Download blocked: the file failed its security check. Please try again later.")
            }
        }

        val signingFlag = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0
        val archive = archiveInfo(file, signingFlag)
            ?: throw UserFacingException("The downloaded file is not a valid Android app. The download link may be broken.")

        if (archive.packageName != expectedPackage) {
            Log.w(TAG, "Wrong APK for ${request.displayName}: expected $expectedPackage got ${archive.packageName} from $finalUrl")
            throw ApkRejectedException(
                "Wrong app blocked: ${request.displayName} should be $expectedPackage but the server sent ${archive.packageName}. Please report this to support."
            )
        }

        val archiveCode = archive.versionCodeCompat
        request.minVersionCode?.let { required ->
            if (archiveCode < required) {
                Log.w(TAG, "Stale APK for ${request.displayName}: got $archiveCode, need $required, from $finalUrl")
                throw ApkRejectedException(
                    "The server's file for ${request.displayName} is older than the version listed in Zappix. Please try again later."
                )
            }
        }

        archive.applicationInfo?.let { checkCompatibility(it, request.displayName) }

        val installed = pm.installedPackageInfo(expectedPackage, signingFlag)
        if (installed != null) {
            if (archiveCode < installed.versionCodeCompat) {
                throw ApkRejectedException("A newer version of ${request.displayName} is already installed on this TV.")
            }
            if (Build.VERSION.SDK_INT >= 28 && !signersCompatible(installed, archive)) {
                throw ApkRejectedException(
                    "${request.displayName} on this TV was installed from a different source. Uninstall it first, then install it from Zappix."
                )
            }
        }
        return PreparedApk(file, archive.packageName, archiveCode)
    }

    private fun checkCompatibility(info: ApplicationInfo, name: String) {
        if (Build.VERSION.SDK_INT >= 24 && info.minSdkVersion > Build.VERSION.SDK_INT) {
            throw ApkRejectedException("$name needs a newer Android version than this TV has.")
        }
        if (Build.VERSION.SDK_INT >= 34 && info.targetSdkVersion < 23) {
            throw ApkRejectedException("$name is built for a very old Android version and can't be installed on this TV.")
        }
    }

    @RequiresApi(28)
    private fun signersCompatible(installed: PackageInfo, archive: PackageInfo): Boolean {
        val installedInfo = installed.signingInfo ?: return true
        val archiveInfo = archive.signingInfo ?: return true
        val installedCurrent = installedInfo.apkContentsSigners.orEmpty().map { it.toCharsString() }.toSet()
        if (installedCurrent.isEmpty()) return true
        return if (archiveInfo.hasMultipleSigners()) {
            archiveInfo.apkContentsSigners.orEmpty().map { it.toCharsString() }.toSet() == installedCurrent
        } else {
            val lineage = (archiveInfo.signingCertificateHistory.orEmpty().toList() +
                archiveInfo.apkContentsSigners.orEmpty().toList())
                .map { it.toCharsString() }
                .toSet()
            installedCurrent.size == 1 && installedCurrent.first() in lineage
        }
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(file: File, flags: Int): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageArchiveInfo(file.absolutePath, flags)
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Hands a verified APK to the system package installer from the foreground Activity.
     * Returns false if no installer could be started.
     */
    fun launchInstaller(activity: Activity, prepared: PreparedApk): Boolean {
        val uri = try {
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", prepared.file)
        } catch (_: Exception) {
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Only let the system installer receive the APK, never a third-party "APK handler".
        systemInstallerPackage(intent)?.let { intent.setPackage(it) }
        return try {
            activity.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            try {
                activity.startActivity(intent.setPackage(null))
                true
            } catch (_: Exception) {
                false
            }
        } catch (_: SecurityException) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun systemInstallerPackage(intent: Intent): String? {
        val matches: List<ResolveInfo> = try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else {
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        } catch (_: Exception) {
            emptyList()
        }
        return matches
            .mapNotNull { it.activityInfo }
            .firstOrNull { (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
            ?.packageName
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

    private companion object {
        const val TAG = "ZappixInstaller"
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
