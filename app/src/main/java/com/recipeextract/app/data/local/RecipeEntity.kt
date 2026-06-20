package com.recipeextract.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val servings: String? = null,
    val prepTime: String? = null,
    val cookTime: String? = null,
    val ingredientsJson: String,
    val instructionsJson: String,
    val difficulty: String,
    val sourceUrl: String,
    val faviconUrl: String? = null,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "url_history")
data class UrlHistoryEntity(
    @PrimaryKey
    val url: String,
    val title: String? = null,
    val accessedAt: Long = System.currentTimeMillis()
)
