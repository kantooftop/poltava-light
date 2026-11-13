package com.poltavalight.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetches raw data from the POE (Poltavaoblenergo) website endpoints.
 * Emulates a standard Android Chrome browser to ensure compatibility
 * with WAF, CDN and anti-bot protection rules.
 */
class PoeApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        private const val BASE_URL = "https://www.poe.pl.ua"
        const val GPV_URL = "$BASE_URL/customs/dynamicgpv-info.php"
        const val EMERGENCY_URL = "$BASE_URL/customs/dynamic-unloading-info.php"

        // Realistic Chrome on Android User-Agent
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36"
    }

    /**
     * Fetch the dynamic GPV schedule page (returns HTML with tables).
     */
    fun fetchGpvHtml(): String {
        val request = Request.Builder()
            .url(GPV_URL)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Connection", "keep-alive")
            .header("Upgrade-Insecure-Requests", "1")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Cache-Control", "no-cache")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                return response.body?.string() ?: throw IOException("Порожня відповідь від сервера")
            }
        } catch (e: Exception) {
            throw IOException(
                "Помилка зв'язку з сайтом poe.pl.ua. Якщо у вас увімкнено VPN, вимкніть його або налаштуйте роздільне тунелювання: ${e.message}",
                e
            )
        }
    }

    /**
     * Fetch emergency outage info (returns JSON array).
     */
    fun fetchEmergencyJson(): String {
        val request = Request.Builder()
            .url(EMERGENCY_URL)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "uk-UA,uk;q=0.9,en;q=0.8")
            .header("Connection", "keep-alive")
            .header("Cache-Control", "no-cache")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    "[]"
                } else {
                    response.body?.string() ?: "[]"
                }
            }
        } catch (e: Exception) {
            "[]"
        }
    }
}
