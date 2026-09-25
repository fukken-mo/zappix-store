package com.zappix.store

data class StoreApp(
    val id: Int,
    val name: String,
    val description: String,
    val iconUrl: String,
    val downloadUrl: String,
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long?,
    val type: AppType,
    val priceLabel: String?
)

enum class AppType { FREE, SUBSCRIPTION, ADULT, TOOLS }
