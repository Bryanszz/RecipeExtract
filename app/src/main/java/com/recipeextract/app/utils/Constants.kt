package com.recipeextract.app.utils

object Constants {
    const val QWEN_MODEL = "qwen-plus"
    const val QWEN_MAX_OUTPUT_TOKENS = 2048
    const val QWEN_BASE_URL = "https://dashscope-intl.aliyuncs.com/api/v1/"
    const val NETWORK_TIMEOUT_SECONDS = 30L
    const val MAX_URL_HISTORY = 20
    const val MAX_CONTENT_LENGTH = 50_000
    const val MAX_YOUTUBE_CONTENT_LENGTH = 8_000

    const val SYSTEM_PROMPT = """
You are a recipe extraction assistant. Extract recipe information from the provided content.
Return ONLY valid JSON with: title, servings, prepTime, cookTime, ingredients (array with name, quantity, unit),
instructions (array of steps), difficulty (easy/medium/hard). If no recipe found, return: {"error": "No recipe found"}
Do not include markdown formatting or code fences. Return raw JSON only.
For YouTube videos: extract the recipe from the video title, description, and transcript. Infer measurements and steps from the description when exact values aren't given.
"""

    val SAMPLE_RECIPES = listOf(
        SampleRecipe(
            title = "Classic Margherita Pizza",
            description = "Simple homemade pizza with fresh basil and mozzarella",
            emoji = "🍕"
        ),
        SampleRecipe(
            title = "Chicken Tikka Masala",
            description = "Creamy Indian curry with tender chicken",
            emoji = "🍛"
        ),
        SampleRecipe(
            title = "Chocolate Chip Cookies",
            description = "Soft and chewy bakery-style cookies",
            emoji = "🍪"
        )
    )
}

data class SampleRecipe(
    val title: String,
    val description: String,
    val emoji: String
)
