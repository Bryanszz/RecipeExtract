package com.recipeextract.app.data.models

import com.google.gson.annotations.SerializedName

data class Ingredient(
    val name: String,
    val quantity: String? = null,
    val unit: String? = null
) {
    fun displayText(): String {
        val parts = listOfNotNull(quantity?.takeIf { it.isNotBlank() }, unit?.takeIf { it.isNotBlank() })
        return if (parts.isEmpty()) {
            name
        } else {
            "${parts.joinToString(" ")} $name".trim()
        }
    }
}

enum class Difficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard");

    companion object {
        fun fromString(value: String?): Difficulty {
            return when (value?.lowercase()) {
                "easy" -> EASY
                "medium" -> MEDIUM
                "hard" -> HARD
                else -> MEDIUM
            }
        }
    }
}

data class Recipe(
    val title: String,
    val servings: String? = null,
    val prepTime: String? = null,
    val cookTime: String? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val instructions: List<String> = emptyList(),
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val sourceUrl: String = "",
    val faviconUrl: String? = null,
    val id: Long = 0L,
    val savedAt: Long? = null
) {
    val totalTime: String?
        get() {
            if (prepTime.isNullOrBlank() && cookTime.isNullOrBlank()) return null
            return listOfNotNull(
                prepTime?.takeIf { it.isNotBlank() }?.let { "Prep: $it" },
                cookTime?.takeIf { it.isNotBlank() }?.let { "Cook: $it" }
            ).joinToString(" · ")
        }

    fun toShareText(): String {
        val builder = StringBuilder()
        builder.appendLine(title)
        builder.appendLine()
        if (!servings.isNullOrBlank()) builder.appendLine("Servings: $servings")
        totalTime?.let { builder.appendLine(it) }
        builder.appendLine("Difficulty: ${difficulty.label}")
        if (sourceUrl.isNotBlank()) builder.appendLine("Source: $sourceUrl")
        builder.appendLine()
        builder.appendLine("INGREDIENTS")
        ingredients.forEach { builder.appendLine("• ${it.displayText()}") }
        builder.appendLine()
        builder.appendLine("INSTRUCTIONS")
        instructions.forEachIndexed { index, step ->
            builder.appendLine("${index + 1}. $step")
        }
        return builder.toString().trim()
    }

    fun ingredientsClipboardText(): String =
        ingredients.joinToString("\n") { "• ${it.displayText()}" }

    fun instructionsClipboardText(): String =
        instructions.mapIndexed { index, step -> "${index + 1}. $step" }.joinToString("\n\n")
}

data class ExtractedRecipeResponse(
    val title: String? = null,
    val servings: String? = null,
    @SerializedName("prepTime") val prepTime: String? = null,
    @SerializedName("cookTime") val cookTime: String? = null,
    val ingredients: List<Ingredient>? = null,
    val instructions: List<String>? = null,
    val difficulty: String? = null,
    val error: String? = null
) {
    fun toRecipe(sourceUrl: String, faviconUrl: String? = null): Recipe? {
        if (!error.isNullOrBlank() || title.isNullOrBlank()) return null
        return Recipe(
            title = title,
            servings = servings,
            prepTime = prepTime,
            cookTime = cookTime,
            ingredients = ingredients.orEmpty(),
            instructions = instructions.orEmpty(),
            difficulty = Difficulty.fromString(difficulty),
            sourceUrl = sourceUrl,
            faviconUrl = faviconUrl
        )
    }
}

sealed class ExtractionResult {
    data class Success(val recipe: Recipe, val fromCache: Boolean = false) : ExtractionResult()
    data class Error(val message: String, val cause: Throwable? = null) : ExtractionResult()
}

sealed class UiState<out T> {
    data object Idle : UiState<Nothing>()
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
