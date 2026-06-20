package com.recipeextract.app.utils

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist
import timber.log.Timber

object HtmlParser {

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun fetchAndParse(url: String): Result<ParsedContent> {
        return runCatching {
            val document = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout((Constants.NETWORK_TIMEOUT_SECONDS * 1000).toInt())
                .followRedirects(true)
                .get()

            if (UrlValidator.isYouTubeUrl(url)) {
                parseYouTube(document, url)
            } else {
                parseWebPage(document, url)
            }
        }.onFailure { Timber.e(it, "Failed to fetch and parse URL: $url") }
    }

    private fun parseWebPage(document: Document, url: String): ParsedContent {
        removeNoise(document)

        // Try JSON-LD recipe schema first
        val jsonLdRecipe = extractJsonLdRecipe(document)
        if (jsonLdRecipe.isNotBlank()) {
            return ParsedContent(
                title = document.title(),
                textContent = jsonLdRecipe,
                sourceUrl = url
            )
        }

        val title = document.select("h1").first()?.text()?.takeIf { it.isNotBlank() }
            ?: document.title()

        val recipeSelectors = listOf(
            "[itemtype*='Recipe']",
            ".recipe",
            ".recipe-content",
            ".wprm-recipe",
            ".tasty-recipes",
            "article"
        )

        var recipeText = ""
        for (selector in recipeSelectors) {
            val element = document.select(selector).firstOrNull()
            if (element != null && element.text().length > 100) {
                recipeText = element.text()
                break
            }
        }

        if (recipeText.isBlank()) {
            recipeText = document.body()?.text().orEmpty()
        }

        return ParsedContent(
            title = title,
            textContent = truncate(recipeText),
            sourceUrl = url
        )
    }

    private fun parseYouTube(document: Document, url: String): ParsedContent {
        val title = document.select("meta[name=title]").attr("content")
            .ifBlank { document.select("meta[property=og:title]").attr("content") }
            .ifBlank { document.title() }

        val description = document.select("meta[name=description]").attr("content")
            .ifBlank { document.select("meta[property=og:description]").attr("content") }

        // Extract extended description from YouTube's page data (not the whole script blob)
        val extendedDescription = extractYouTubeDescription(document)

        // Attempt to find transcript/caption data in page scripts
        val transcriptText = extractCaptionTrackUrl(document)?.let { fetchTranscriptText(it) }.orEmpty()

        val combined = buildString {
            appendLine("Video Title: $title")
            if (extendedDescription.isNotBlank()) {
                appendLine("Description: $extendedDescription")
            } else if (description.isNotBlank()) {
                appendLine("Description: $description")
            }
            if (transcriptText.isNotBlank()) {
                appendLine("Transcript: $transcriptText")
            }
        }

        return ParsedContent(
            title = title,
            textContent = truncateYouTube(combined),
            sourceUrl = url,
            isYouTube = true
        )
    }

    private fun removeNoise(document: Document) {
        document.select(
            "script, style, nav, footer, header, aside, .ad, .ads, .advertisement, " +
                ".social-share, .comments, .comment, .sidebar, .newsletter, .popup"
        ).remove()
    }

    private fun extractJsonLdRecipe(document: Document): String {
        val scripts = document.select("script[type=application/ld+json]")
        for (script in scripts) {
            val json = script.data()
            if (json.contains("Recipe", ignoreCase = true)) {
                return truncate(json)
            }
        }
        return ""
    }

    private fun extractYouTubeDescription(document: Document): String {
        // Extract shortDescription from YouTube's ytInitialPlayerResponse without
        // returning the entire script blob (which can be 100KB+ of raw JSON)
        val scripts = document.select("script")
        for (script in scripts) {
            val data = script.data()
            if (data.contains("shortDescription")) {
                // Use raw string to avoid Kotlin escaping issues with regex
                val regex = Regex(""""shortDescription"\s*:\s*"((?:[^\\"]|\\.)*)"""")
                val match = regex.find(data)
                if (match != null) {
                    val desc = match.groupValues[1]
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\/", "/")
                    return desc
                }
            }
        }
        return ""
    }
    
    private fun extractCaptionTrackUrl(document: Document): String? {
        val scripts = document.select("script")
        for (script in scripts) {
            val data = script.data()
            if (data.contains("captionTracks")) {
                return extractCaptionTrackUrlFromData(data)
            }
        }
        return null
    }

    private fun extractCaptionTrackUrlFromData(scriptData: String): String? {
        val regex = Regex("\"baseUrl\"\\s*:\\s*\"(https:[^\"]+)\"")
        return regex.find(scriptData)?.groupValues?.get(1)
            ?.replace("\\u0026", "&")
    }

    private fun fetchTranscriptText(captionUrl: String): String {
        return runCatching {
            val xml = Jsoup.connect(captionUrl)
                .userAgent(USER_AGENT)
                .timeout(15_000)
                .ignoreContentType(true)
                .execute()
                .body()

            Jsoup.parse(xml).select("text").joinToString(" ") { it.text() }
        }.getOrDefault("")
    }

    private fun truncateYouTube(text: String): String {
        val cleaned = Jsoup.clean(text, Safelist.none())
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (cleaned.length > Constants.MAX_YOUTUBE_CONTENT_LENGTH) {
            cleaned.take(Constants.MAX_YOUTUBE_CONTENT_LENGTH) + "…"
        } else {
            cleaned
        }
    }

    private fun truncate(text: String): String {
        val cleaned = Jsoup.clean(text, Safelist.none())
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (cleaned.length > Constants.MAX_CONTENT_LENGTH) {
            cleaned.take(Constants.MAX_CONTENT_LENGTH) + "…"
        } else {
            cleaned
        }
    }
}

data class ParsedContent(
    val title: String,
    val textContent: String,
    val sourceUrl: String,
    val isYouTube: Boolean = false
)
