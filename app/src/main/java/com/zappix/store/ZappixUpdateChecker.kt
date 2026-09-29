package com.zappix.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class ZappixUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val message: String,
    val required: Boolean,
    val sha256: String?
)

class ZappixUpdateChecker(
    private val client: OkHttpClient = Net.api
) {
    /** Returns the update only when the server offers a newer build than the one running. */
    suspend fun check(): ZappixUpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/update.php")
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache, no-store")
            .header("Pragma", "no-cache")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val root = JSONObject(response.body?.string().orEmpty())
            if (!root.flag("success")) return@use null
            val info = ZappixUpdateInfo(
                versionCode = root.longOrNull("version_code") ?: 0L,
                versionName = root.stringOrNull("version_name").orEmpty(),
                apkUrl = root.stringOrNull("apk_url").orEmpty(),
                message = root.stringOrNull("message").orEmpty(),
                required = root.flag("required"),
                sha256 = root.stringOrNull("sha256")?.lowercase()
            )
            info.takeIf { it.versionCode > BuildConfig.VERSION_CODE && it.apkUrl.isNotEmpty() }
        }
    }
}
