package com.recipeextract.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.recipeextract.app.data.local.RecipeDatabase
import com.recipeextract.app.data.models.Difficulty
import com.recipeextract.app.data.models.Ingredient
import com.recipeextract.app.data.models.Recipe
import com.recipeextract.app.data.repository.RecipeRepository
import com.recipeextract.app.test.CoroutineTestRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.google.common.truth.Truth.assertThat

@RunWith(RobolectricTestRunner::class)
class RecipeRepositoryIntegrationTest : CoroutineTestRule() {

    private lateinit var database: RecipeDatabase
    private lateinit var repository: RecipeRepository
    private val gson = Gson()

    @Before
    override fun setUp() {
        super.setUp()
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RecipeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RecipeRepository(database.recipeDao(), database.urlHistoryDao(), gson)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveAndRetrieveRecipe() = runTest {
        val recipe = Recipe(
            title = "Pasta",
            servings = "4",
            ingredients = listOf(Ingredient("pasta", "400", "g")),
            instructions = listOf("Boil water", "Cook pasta"),
            difficulty = Difficulty.EASY,
            sourceUrl = "https://example.com/pasta"
        )

        val id = repository.saveRecipe(recipe)
        val loaded = repository.getRecipeById(id)

        assertThat(loaded).isNotNull()
        assertThat(loaded?.title).isEqualTo("Pasta")
        assertThat(loaded?.ingredients?.first()?.name).isEqualTo("pasta")
    }

    @Test
    fun searchRecipes_byIngredient() = runTest {
        repository.saveRecipe(
            Recipe(
                title = "Salad",
                ingredients = listOf(Ingredient("tomato", "2", null)),
                instructions = listOf("Chop"),
                sourceUrl = "https://example.com/salad"
            )
        )
        repository.saveRecipe(
            Recipe(
                title = "Soup",
                ingredients = listOf(Ingredient("carrot", "3", null)),
                instructions = listOf("Simmer"),
                sourceUrl = "https://example.com/soup"
            )
        )

        val results = repository.searchSavedRecipes("tomato").first()
        assertThat(results).hasSize(1)
        assertThat(results.first().title).isEqualTo("Salad")
    }

    @Test
    fun urlHistory_trimsToMaxItems() = runTest {
        repeat(25) { index ->
            repository.addUrlToHistory("https://example.com/$index", "Recipe $index")
        }

        val history = repository.getRecentUrls().first()
        assertThat(history).hasSize(20)
    }
}
