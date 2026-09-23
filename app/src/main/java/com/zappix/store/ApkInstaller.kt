package com.zappix.store

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class ApkInstaller(private val context: Context) {
    private val client = OkHttpClient()

    suspend fun downloadAndOpenInstaller(app: StoreApp, onProgress: (Int) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                withContext(Dispatchers.Main) { context.startActivity(settingsIntent) }
                error("Allow Zappix to install unknown apps, then select Download / Install again.")
            }

            val uri = Uri.parse(app.downloadUrl)
            require(uri.scheme == "https" || uri.scheme == "http") { "Invalid download URL" }

            val request = Request.Builder().url(app.downloadUrl).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Download failed (${response.code})" }
                val body = response.body ?: error("Empty download")
                val total = body.contentLength()
                val dir = File(context.cacheDir, "apks").apply { mkdirs() }
                val safeName = app.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "zappix-app" }
                val file = File(dir, "$safeName.apk")

                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) {
                                onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val install = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                withContext(Dispatchers.Main) { context.startActivity(install) }
            }
        }
    }
}
