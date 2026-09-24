package com.zappix.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class StoreApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    suspend fun loadApps(): List<StoreApp> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/apps.php")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Server returned ${response.code}" }
            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            check(root.optBoolean("success", false)) { root.optString("message", "Unable to load apps") }
            val array = root.getJSONArray("apps")
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        StoreApp(
                            id = item.getInt("id"),
                            name = item.getString("name"),
                            description = item.optString("description"),
                            iconUrl = item.optString("icon_url"),
                            downloadUrl = item.getString("download_url"),
                            packageName = item.optString("package_name").takeIf { it.isNotBlank() },
                            type = when (item.optString("app_type").lowercase()) {
                                "subscription" -> AppType.SUBSCRIPTION
                                "adult" -> AppType.ADULT
                                else -> AppType.FREE
                            },
                            priceLabel = item.optString("price_label").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }
    }
}
