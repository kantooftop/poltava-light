package com.poltavalight.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches raw data from the POE (Poltavaoblenergo) website endpoints.
 */
class PoeApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val BASE_URL = "https://www.poe.pl.ua"
        const val GPV_URL = "$BASE_URL/customs/dynamicgpv-info.php"
        const val EMERGENCY_URL = "$BASE_URL/customs/dynamic-unloading-info.php"
        private const val USER_AGENT = "PoltavaLight/1.0 (Android)"
    }

    /**
     * Fetch the dynamic GPV schedule page (returns HTML with tables).
     */
    fun fetchGpvHtml(): String {
        val request = Request.Builder()
            .url(GPV_URL)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string() ?: throw Exception("Empty response body")
        }
    }

    /**
     * Fetch emergency outage info (returns JSON array).
     */
    fun fetchEmergencyJson(): String {
        val request = Request.Builder()
            .url(EMERGENCY_URL)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string() ?: "[]"
        }
    }
}
