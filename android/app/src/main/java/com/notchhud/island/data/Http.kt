package com.notchhud.island.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Returns the body, or null on any transport/HTTP failure — callers render an empty state. */
    fun getString(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", "IslandHUD/1.0").build()).execute().use { r ->
            if (!r.isSuccessful) null else r.body?.string()
        }
    }.getOrNull()
}
