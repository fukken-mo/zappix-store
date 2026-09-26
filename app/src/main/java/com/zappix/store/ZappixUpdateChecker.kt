package com.zappix.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ZappixUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val message: String,
    val required: Boolean
)

class ZappixUpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    suspend fun check(): Result<ZappixUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = BuildConfig.API_BASE_URL.trimEnd('/') + "/update.php"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache, no-store")
                .header("Pragma", "no-cache")
                .build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Update server returned ${response.code}" }
                val root = JSONObject(response.body?.string().orEmpty())
                check(root.optBoolean("success", false)) { "Update check failed" }
                ZappixUpdateInfo(
                    versionCode = root.optLong("version_code", 0),
                    versionName = root.optString("version_name", ""),
                    apkUrl = root.optString("apk_url", ""),
                    message = root.optString("message", ""),
                    required = root.optBoolean("required", false)
                )
            }
        }
    }
}
