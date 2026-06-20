package com.recipeextract.app.utils

import android.util.Patterns
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

sealed class UrlValidationResult {
    data class Valid(val normalizedUrl: String, val isYouTube: Boolean) : UrlValidationResult()
    data class Invalid(val message: String) : UrlValidationResult()
}

object UrlValidator {

    private val youtubePatterns = listOf(
        Regex("^(https?://)?(www\\.)?youtube\\.com/watch\\?.*v=[\\w-]+", RegexOption.IGNORE_CASE),
        Regex("^(https?://)?(www\\.)?youtu\\.be/[\\w-]+", RegexOption.IGNORE_CASE),
        Regex("^(https?://)?(www\\.)?youtube\\.com/shorts/[\\w-]+", RegexOption.IGNORE_CASE)
    )

    fun validate(url: String): UrlValidationResult {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            return UrlValidationResult.Invalid("Please enter a URL")
        }

        val normalized = normalizeUrl(trimmed)
        if (!Patterns.WEB_URL.matcher(normalized).matches()) {
            return UrlValidationResult.Invalid("Invalid URL format. Please enter a valid web address.")
        }

        val uri = runCatching { URI(normalized) }.getOrNull()
        if (uri == null || uri.host.isNullOrBlank()) {
            return UrlValidationResult.Invalid("Invalid URL format. Please enter a valid web address.")
        }

        if (!normalized.startsWith("http://", ignoreCase = true) &&
            !normalized.startsWith("https://", ignoreCase = true)
        ) {
            return UrlValidationResult.Invalid("URL must start with http:// or https://")
        }

        val isYouTube = isYouTubeUrl(normalized)
        return UrlValidationResult.Valid(normalized, isYouTube)
    }

    fun normalizeUrl(url: String): String {
        var result = url.trim()
        if (!result.startsWith("http://", ignoreCase = true) &&
            !result.startsWith("https://", ignoreCase = true)
        ) {
            result = "https://$result"
        }

        // Convert youtu.be short URLs to full youtube.com URLs so the page
        // can be properly fetched and parsed by HtmlParser
        val youtuBeRegex = Regex(
            "https?://(?:www\\.)?youtu\\.be/([\\w-]+)(?:\\?.*)?",
            RegexOption.IGNORE_CASE
        )
        youtuBeRegex.find(result)?.let { match ->
            val videoId = match.groupValues[1]
            result = "https://www.youtube.com/watch?v=$videoId"
            Timber.i("Normalized youtu.be URL to: $result")
        }

        return result
    }

    fun isYouTubeUrl(url: String): Boolean {
        return youtubePatterns.any { it.containsMatchIn(url) }
    }

    fun getFaviconUrl(url: String): String? {
        return runCatching {
            val host = URI(url).host ?: return null
            "https://www.google.com/s2/favicons?domain=$host&sz=64"
        }.getOrNull()
    }

    /**
     * Lightweight HEAD request to verify the URL is reachable.
     */
    suspend fun checkAccessibility(url: String): Result<Unit> {
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "HEAD"
                connectTimeout = (Constants.NETWORK_TIMEOUT_SECONDS * 1000).toInt()
                readTimeout = (Constants.NETWORK_TIMEOUT_SECONDS * 1000).toInt()
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", HtmlParser.USER_AGENT)
            }
            val responseCode = connection.responseCode
            connection.disconnect()
            when {
                responseCode in 200..399 -> Unit
                responseCode == 403 -> Unit // Some sites block HEAD but allow GET
                responseCode == 405 -> Unit // Method not allowed, try extraction anyway
                else -> throw IllegalStateException("Unable to reach URL (HTTP $responseCode)")
            }
        }
    }
}
