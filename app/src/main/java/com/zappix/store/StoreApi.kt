package com.zappix.store

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

class StoreApi(
    private val client: OkHttpClient = Net.api
) {
    suspend fun loadApps(): List<StoreApp> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/apps.php")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UserFacingException("Zappix server error (${response.code}). Please try again later.")
            }
            val body = response.body?.string().orEmpty()
            val root = try {
                JSONObject(body)
            } catch (_: JSONException) {
                throw UserFacingException("The Zappix server sent an invalid response. Please try again later.")
            }
            if (!root.flag("success")) {
                throw UserFacingException(root.stringOrNull("message") ?: "Unable to load apps.")
            }
            val array = root.optJSONArray("apps")
                ?: throw UserFacingException("The Zappix server sent an invalid response. Please try again later.")
            val seenIds = HashSet<Int>()
            buildList {
                for (i in 0 until array.length()) {
                    val app = array.optJSONObject(i)?.let(::parseApp)
                    if (app == null) {
                        Log.w(TAG, "Skipping invalid catalog entry at index $i")
                        continue
                    }
                    if (seenIds.add(app.id)) add(app) else Log.w(TAG, "Skipping duplicate catalog id ${app.id}")
                }
            }
        }
    }

    /** One malformed entry must not take the whole store down, so bad entries are skipped. */
    private fun parseApp(item: JSONObject): StoreApp? {
        val id = item.longOrNull("id")?.toInt() ?: return null
        val name = item.stringOrNull("name") ?: return null
        val downloadUrl = item.stringOrNull("download_url") ?: return null
        return StoreApp(
            id = id,
            name = name,
            description = item.stringOrNull("description").orEmpty(),
            iconUrl = item.stringOrNull("icon_url").orEmpty(),
            downloadUrl = downloadUrl,
            packageName = item.stringOrNull("package_name"),
            versionName = item.stringOrNull("version_name"),
            versionCode = item.longOrNull("version_code")?.takeIf { it > 0 },
            type = when (item.stringOrNull("app_type")?.lowercase()) {
                "subscription" -> AppType.SUBSCRIPTION
                "adult" -> AppType.ADULT
                "tools" -> AppType.TOOLS
                else -> AppType.FREE
            },
            priceLabel = item.stringOrNull("price_label"),
            sha256 = item.stringOrNull("sha256")?.lowercase()
        )
    }

    private companion object {
        const val TAG = "ZappixStoreApi"
    }
}
