package com.recipeextract.app.viewmodel

import com.recipeextract.app.data.models.ExtractionResult
import com.recipeextract.app.data.models.Ingredient
import com.recipeextract.app.data.models.Recipe
import com.recipeextract.app.data.models.UiState
import com.recipeextract.app.data.repository.ApiRepository
import com.recipeextract.app.data.repository.RecipeRepository
import com.recipeextract.app.test.CoroutineTestRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class HomeViewModelTest : CoroutineTestRule() {

    private val apiRepository: ApiRepository = mockk()
    private val recipeRepository: RecipeRepository = mockk(relaxed = true)
    private lateinit var viewModel: HomeViewModel

    @Before
    override fun setUp() {
        super.setUp()
        every { recipeRepository.getRecentUrls() } returns flowOf(emptyList())
        viewModel = HomeViewModel(apiRepository, recipeRepository)
    }

    @Test
    fun extractRecipe_withInvalidUrl_setsValidationError() = runTest {
        viewModel.onUrlChanged("not a url")
        viewModel.extractRecipe()

        assertThat(viewModel.validationError.value).isNotNull()
    }

    @Test
    fun extractRecipe_withValidUrl_emitsSuccess() = runTest {
        val recipe = Recipe(
            title = "Test Cookies",
            ingredients = listOf(Ingredient("flour", "2", "cups")),
            instructions = listOf("Mix", "Bake"),
            sourceUrl = "https://example.com/recipe"
        )
        coEvery { apiRepository.extractRecipeFromUrl(any()) } returns ExtractionResult.Success(recipe)

        viewModel.onUrlChanged("https://example.com/recipe")
        viewModel.extractRecipe()

        assertThat(viewModel.extractionState.value).isInstanceOf(UiState.Success::class.java)
        coVerify { recipeRepository.addUrlToHistory("https://example.com/recipe", "Test Cookies") }
    }

    @Test
    fun extractRecipe_whenApiFails_emitsError() = runTest {
        coEvery { apiRepository.extractRecipeFromUrl(any()) } returns ExtractionResult.Error("No recipe found")

        viewModel.onUrlChanged("https://example.com/page")
        viewModel.extractRecipe()

        assertThat(viewModel.extractionState.value).isInstanceOf(UiState.Error::class.java)
    }
}
