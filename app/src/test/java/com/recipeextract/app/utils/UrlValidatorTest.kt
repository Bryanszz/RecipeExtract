package com.recipeextract.app.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UrlValidatorTest {

    @Test
    fun validate_blankUrl_returnsInvalid() {
        val result = UrlValidator.validate("")
        assertThat(result).isInstanceOf(UrlValidationResult.Invalid::class.java)
        assertThat((result as UrlValidationResult.Invalid).message).contains("enter a URL")
    }

    @Test
    fun validate_validBlogUrl_returnsValid() {
        val result = UrlValidator.validate("https://www.allrecipes.com/recipe/10813/best-chocolate-chip-cookies/")
        assertThat(result).isInstanceOf(UrlValidationResult.Valid::class.java)
        val valid = result as UrlValidationResult.Valid
        assertThat(valid.isYouTube).isFalse()
    }

    @Test
    fun validate_youtubeUrl_detectsYouTube() {
        val result = UrlValidator.validate("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertThat(result).isInstanceOf(UrlValidationResult.Valid::class.java)
        assertThat((result as UrlValidationResult.Valid).isYouTube).isTrue()
    }

    @Test
    fun validate_shortYouTubeUrl_detectsYouTube() {
        val result = UrlValidator.validate("https://youtu.be/abc123")
        assertThat(result).isInstanceOf(UrlValidationResult.Valid::class.java)
        assertThat((result as UrlValidationResult.Valid).isYouTube).isTrue()
    }

    @Test
    fun validate_missingScheme_addsHttps() {
        val result = UrlValidator.validate("example.com/recipe")
        assertThat(result).isInstanceOf(UrlValidationResult.Valid::class.java)
        assertThat((result as UrlValidationResult.Valid).normalizedUrl).startsWith("https://")
    }

    @Test
    fun getFaviconUrl_returnsGoogleFaviconService() {
        val favicon = UrlValidator.getFaviconUrl("https://www.allrecipes.com/recipe/test")
        assertThat(favicon).contains("google.com/s2/favicons")
        assertThat(favicon).contains("allrecipes.com")
    }
}
