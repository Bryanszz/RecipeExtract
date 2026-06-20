package com.recipeextract.app.data.repository

import com.google.gson.Gson
import com.recipeextract.app.data.models.ExtractionResult
import com.recipeextract.app.data.models.RecipeRequest
import com.recipeextract.app.data.remote.QwenApiService
import com.recipeextract.app.test.CoroutineTestRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import retrofit2.Call
import retrofit2.Response

class ApiRepositoryTest : CoroutineTestRule() {

    private val qwenApiService: QwenApiService = mockk()
    private val recipeRepository: RecipeRepository = mockk(relaxed = true)
    private val gson = Gson()
    private lateinit var repository: ApiRepository

    @Before
    override fun setUp() {
        super.setUp()
        coEvery { recipeRepository.getRecipeBySourceUrl(any()) } returns null
        repository = ApiRepository(qwenApiService, recipeRepository, gson)
    }

    private fun mockQwenCall(text: String): Call<okhttp3.ResponseBody> {
        val json = """{"output":{"text":${Gson().toJson(text)},"finish_reason":"stop"},"usage":{"input_tokens":10,"output_tokens":50},"request_id":"test-123"}"""
        val body = json.toResponseBody("application/json".toMediaType())
        val call: Call<okhttp3.ResponseBody> = mockk()
        every { call.execute() } returns Response.success(body)
        return call
    }

    @Test
    fun extractRecipeWithQwen_parsesValidJsonResponse() = runTest {
        val recipeJson = """
            {
              "title": "Garlic Bread",
              "servings": "4",
              "prepTime": "10 min",
              "cookTime": "15 min",
              "ingredients": [{"name": "baguette", "quantity": "1", "unit": null}],
              "instructions": ["Slice bread", "Bake"],
              "difficulty": "easy"
            }
        """.trimIndent()

        every { qwenApiService.generateContent(any(), any()) } returns mockQwenCall(recipeJson)

        val result = repository.extractRecipeWithQwen(
            RecipeRequest(
                url = "https://example.com/garlic-bread",
                content = "Garlic bread recipe content"
            )
        )

        assertThat(result).isInstanceOf(ExtractionResult.Success::class.java)
        val recipe = (result as ExtractionResult.Success).recipe
        assertThat(recipe.title).isEqualTo("Garlic Bread")
        assertThat(recipe.ingredients).hasSize(1)
    }

    @Test
    fun extractRecipeWithQwen_returnsErrorWhenNoRecipe() = runTest {
        every { qwenApiService.generateContent(any(), any()) } returns mockQwenCall(
            """{"error": "No recipe found"}"""
        )

        val result = repository.extractRecipeWithQwen(
            RecipeRequest(
                url = "https://example.com/blog",
                content = "Random blog post without recipe"
            )
        )

        assertThat(result).isInstanceOf(ExtractionResult.Error::class.java)
    }
}
