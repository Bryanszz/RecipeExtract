package com.recipeextract.app.data.repository

import com.google.gson.Gson
import com.recipeextract.app.data.local.RecipeDao
import com.recipeextract.app.data.local.RecipeMapper
import com.recipeextract.app.data.local.UrlHistoryDao
import com.recipeextract.app.data.local.UrlHistoryEntity
import com.recipeextract.app.data.models.Recipe
import com.recipeextract.app.utils.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val urlHistoryDao: UrlHistoryDao,
    private val gson: Gson
) {

    fun getSavedRecipes(): Flow<List<Recipe>> =
        recipeDao.getAllRecipes().map { entities ->
            entities.map { RecipeMapper.fromEntity(it, gson) }
        }

    fun searchSavedRecipes(query: String): Flow<List<Recipe>> =
        recipeDao.searchRecipes(query.trim()).map { entities ->
            entities.map { RecipeMapper.fromEntity(it, gson) }
        }

    suspend fun getRecipeById(id: Long): Recipe? {
        val entity = recipeDao.getRecipeById(id) ?: return null
        return RecipeMapper.fromEntity(entity, gson)
    }

    suspend fun saveRecipe(recipe: Recipe): Long {
        val entity = RecipeMapper.toEntity(recipe, gson)
        return recipeDao.insertRecipe(entity)
    }

    suspend fun updateRecipe(recipe: Recipe) {
        recipeDao.updateRecipe(RecipeMapper.toEntity(recipe, gson))
    }

    suspend fun deleteRecipe(recipe: Recipe) {
        if (recipe.id > 0) {
            recipeDao.deleteRecipeById(recipe.id)
        } else {
            recipeDao.deleteRecipe(RecipeMapper.toEntity(recipe, gson))
        }
    }

    suspend fun isRecipeSaved(sourceUrl: String): Boolean =
        recipeDao.isRecipeSaved(sourceUrl)

    suspend fun getRecipeBySourceUrl(sourceUrl: String): Recipe? {
        val entity = recipeDao.getRecipeBySourceUrl(sourceUrl) ?: return null
        return RecipeMapper.fromEntity(entity, gson)
    }

    fun getRecentUrls(): Flow<List<UrlHistoryEntity>> =
        urlHistoryDao.getRecentUrls(Constants.MAX_URL_HISTORY)

    suspend fun addUrlToHistory(url: String, title: String? = null) {
        urlHistoryDao.insertUrl(
            UrlHistoryEntity(url = url, title = title, accessedAt = System.currentTimeMillis())
        )
        urlHistoryDao.trimToLimit(Constants.MAX_URL_HISTORY)
    }
}
