package com.recipeextract.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query("SELECT * FROM saved_recipes ORDER BY savedAt DESC")
    fun getAllRecipes(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM saved_recipes WHERE id = :id")
    suspend fun getRecipeById(id: Long): RecipeEntity?

    @Query(
        """
        SELECT * FROM saved_recipes 
        WHERE title LIKE '%' || :query || '%' 
        OR ingredientsJson LIKE '%' || :query || '%'
        ORDER BY savedAt DESC
        """
    )
    fun searchRecipes(query: String): Flow<List<RecipeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Delete
    suspend fun deleteRecipe(recipe: RecipeEntity)

    @Query("DELETE FROM saved_recipes WHERE id = :id")
    suspend fun deleteRecipeById(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_recipes WHERE sourceUrl = :sourceUrl)")
    suspend fun isRecipeSaved(sourceUrl: String): Boolean

    @Query("SELECT * FROM saved_recipes WHERE sourceUrl = :sourceUrl LIMIT 1")
    suspend fun getRecipeBySourceUrl(sourceUrl: String): RecipeEntity?
}

@Dao
interface UrlHistoryDao {

    @Query("SELECT * FROM url_history ORDER BY accessedAt DESC LIMIT :limit")
    fun getRecentUrls(limit: Int = 20): Flow<List<UrlHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUrl(urlHistory: UrlHistoryEntity)

    @Query("DELETE FROM url_history WHERE url NOT IN (SELECT url FROM url_history ORDER BY accessedAt DESC LIMIT :limit)")
    suspend fun trimToLimit(limit: Int = 20)

    @Query("DELETE FROM url_history")
    suspend fun clearAll()
}
