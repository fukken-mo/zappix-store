package com.zappix.store

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import okhttp3.OkHttpClient
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

object Net {
    /** One shared connection pool / dispatcher for the whole app. */
    private val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Catalog + update checks: redirects allowed, but never https -> http. */
    val api: OkHttpClient by lazy {
        base.newBuilder()
            .followRedirects(true)
            .followSslRedirects(false)
            .build()
    }

    /** APK downloads: redirects are followed manually so every hop can be validated. */
    val download: OkHttpClient by lazy {
        base.newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}

/** An error whose message is safe and meaningful to show on screen. */
open class UserFacingException(message: String) : Exception(message)

/** The downloaded APK was rejected; retrying the same download will not help. */
class ApkRejectedException(message: String) : UserFacingException(message)

fun Throwable.userMessage(fallback: String): String {
    val raw = message.orEmpty()
    return when {
        this is UserFacingException -> raw.ifBlank { fallback }
        raw.contains("ENOSPC") || raw.contains("No space left", ignoreCase = true) ->
            "Not enough free storage on this TV. Free up some space and try again."
        this is UnknownHostException || this is ConnectException || this is SocketTimeoutException ->
            "Can't reach the server. Check the TV's internet connection and try again."
        this is SSLException ->
            "Secure connection failed. Check the TV's date and time, then try again."
        this is IOException -> "The connection was interrupted. Please try again."
        this is JSONException -> "The server sent an invalid response. Please try again later."
        else -> fallback
    }
}

/** JSON helpers: Android's optString() returns the text "null" for JSON null values. */
fun JSONObject.stringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key).trim()
    return value.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

fun JSONObject.longOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is Number -> value.toLong()
        is String -> value.trim().toLongOrNull()
        else -> null
    }
}

fun JSONObject.flag(key: String): Boolean {
    if (!has(key) || isNull(key)) return false
    return when (val value = opt(key)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.trim().lowercase() in setOf("true", "1", "yes")
        else -> false
    }
}

private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

fun isValidPackageName(name: String?): Boolean = name != null && PACKAGE_NAME_REGEX.matches(name)

@Suppress("DEPRECATION")
fun PackageManager.installedPackageInfo(packageName: String, flags: Int = 0): PackageInfo? = try {
    if (Build.VERSION.SDK_INT >= 33) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        getPackageInfo(packageName, flags)
    }
} catch (_: Exception) {
    null
}

val PackageInfo.versionCodeCompat: Long
    @Suppress("DEPRECATION")
    get() = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()
