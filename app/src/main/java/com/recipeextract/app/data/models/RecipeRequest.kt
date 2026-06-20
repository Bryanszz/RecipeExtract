package com.recipeextract.app.data.models

data class RecipeRequest(
    val url: String,
    val content: String,
    val contentType: ContentType = ContentType.WEBPAGE
)

enum class ContentType {
    WEBPAGE,
    YOUTUBE
}
