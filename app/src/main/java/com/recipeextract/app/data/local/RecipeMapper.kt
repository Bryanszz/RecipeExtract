package com.recipeextract.app.data.local

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.recipeextract.app.data.models.Difficulty
import com.recipeextract.app.data.models.Ingredient
import com.recipeextract.app.data.models.Recipe

object RecipeMapper {

    fun toEntity(recipe: Recipe, gson: Gson): RecipeEntity {
        return RecipeEntity(
            id = recipe.id,
            title = recipe.title,
            servings = recipe.servings,
            prepTime = recipe.prepTime,
            cookTime = recipe.cookTime,
            ingredientsJson = gson.toJson(recipe.ingredients),
            instructionsJson = gson.toJson(recipe.instructions),
            difficulty = recipe.difficulty.name,
            sourceUrl = recipe.sourceUrl,
            faviconUrl = recipe.faviconUrl,
            savedAt = recipe.savedAt ?: System.currentTimeMillis()
        )
    }

    fun fromEntity(entity: RecipeEntity, gson: Gson): Recipe {
        val ingredients = parseIngredients(entity.ingredientsJson)
        val instructions = parseInstructions(entity.instructionsJson)
        return Recipe(
            id = entity.id,
            title = entity.title,
            servings = entity.servings,
            prepTime = entity.prepTime,
            cookTime = entity.cookTime,
            ingredients = ingredients,
            instructions = instructions,
            difficulty = runCatching { Difficulty.valueOf(entity.difficulty) }.getOrDefault(Difficulty.MEDIUM),
            sourceUrl = entity.sourceUrl,
            faviconUrl = entity.faviconUrl,
            savedAt = entity.savedAt
        )
    }

    private fun parseIngredients(json: String): List<Ingredient> {
        return try {
            val array = JsonParser.parseString(json).asJsonArray
            array.mapNotNull { item ->
                when {
                    item.isJsonObject -> {
                        val obj = item.asJsonObject
                        val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString
                            ?: return@mapNotNull null
                        Ingredient(
                            name = name,
                            quantity = obj.get("quantity")?.takeIf { !it.isJsonNull }?.asString,
                            unit = obj.get("unit")?.takeIf { !it.isJsonNull }?.asString
                        )
                    }
                    item.isJsonPrimitive -> Ingredient(name = item.asString)
                    else -> null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseInstructions(json: String): List<String> {
        return try {
            val array = JsonParser.parseString(json).asJsonArray
            array.mapNotNull { item ->
                if (item.isJsonPrimitive) item.asString else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
