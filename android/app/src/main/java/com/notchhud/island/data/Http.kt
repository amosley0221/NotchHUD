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

    /**
     * The body, or the reason it could not be had.
     *
     * The reason matters: swallowing every failure into a null meant a settings
     * screen that could only say "could not reach" for all eight leagues at once,
     * which is true of a blocked request, a DNS failure and a 403 alike.
     */
    sealed interface Result {
        data class Ok(val body: String) : Result
        data class Failed(val reason: String) : Result
    }

    fun get(url: String): Result = try {
        client.newCall(
            Request.Builder().url(url).header("User-Agent", "IslandHUD/1.0").build()
        ).execute().use { response ->
            val body = response.body?.string()
            when {
                !response.isSuccessful -> Result.Failed("HTTP ${response.code}")
                body.isNullOrBlank() -> Result.Failed("empty response")
                else -> Result.Ok(body)
            }
        }
    } catch (t: Throwable) {
        Result.Failed(t.javaClass.simpleName + (t.message?.let { ": $it" } ?: ""))
    }

    /** Convenience for callers that genuinely do not care why. */
    fun getString(url: String): String? = (get(url) as? Result.Ok)?.body
}
