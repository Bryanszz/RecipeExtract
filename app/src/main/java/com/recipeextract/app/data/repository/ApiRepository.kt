package com.recipeextract.app.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.recipeextract.app.BuildConfig
import com.recipeextract.app.data.models.ContentType
import com.recipeextract.app.data.models.ExtractedRecipeResponse
import com.recipeextract.app.data.models.ExtractionResult
import com.recipeextract.app.data.models.Ingredient
import com.recipeextract.app.data.models.QwenInput
import com.recipeextract.app.data.models.QwenMessage
import com.recipeextract.app.data.models.QwenParameters
import com.recipeextract.app.data.models.QwenRequest
import com.recipeextract.app.data.models.RecipeRequest
import com.recipeextract.app.data.remote.QwenApiService
import com.recipeextract.app.utils.Constants
import com.recipeextract.app.utils.HtmlParser
import com.recipeextract.app.utils.UrlValidationResult
import com.recipeextract.app.utils.UrlValidator
import android.util.Log as ALog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiRepository @Inject constructor(
    private val qwenApiService: QwenApiService,
    private val recipeRepository: RecipeRepository,
    private val gson: Gson
) {
    private var lastApiCallTime = 0L
    private val MIN_TIME_BETWEEN_CALLS = 3000L

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private val RETRY_DELAYS = listOf(5000L, 10000L, 15000L)
    }

    suspend fun extractRecipeFromUrl(url: String): ExtractionResult = withContext(Dispatchers.IO) {
        ALog.d("RecipeExtract", "=== extractRecipeFromUrl started for: $url ===")

        if (BuildConfig.QWEN_API_KEY.isBlank()) {
            return@withContext ExtractionResult.Error(
                "API key not configured. Add QWEN_API_KEY to local.properties"
            )
        }

        val validation = UrlValidator.validate(url)
        if (validation is UrlValidationResult.Invalid) {
            return@withContext ExtractionResult.Error(validation.message)
        }

        val validUrl = (validation as UrlValidationResult.Valid).normalizedUrl

        // Check cache before making any network requests
        try {
            recipeRepository.getRecipeBySourceUrl(validUrl)?.let { cachedRecipe ->
                ALog.d("RecipeExtract", "Recipe found in cache for URL: $validUrl")
                return@withContext ExtractionResult.Success(cachedRecipe, fromCache = true)
            }
        } catch (e: Exception) {
            ALog.e("RecipeExtract", "Cache lookup failed: ${e.javaClass.simpleName}: ${e.message}", e)
            // Continue with network extraction if cache fails
        }

        UrlValidator.checkAccessibility(validUrl).onFailure { error ->
            Timber.w(error, "URL accessibility check failed, proceeding anyway")
        }

        val parsedContent = HtmlParser.fetchAndParse(validUrl).getOrElse { error ->
            ALog.e("RecipeExtract", "Failed to fetch page: ${error.javaClass.simpleName}: ${error.message}")
            return@withContext mapNetworkError(error, "Failed to fetch page content")
        }

        ALog.d("RecipeExtract", "Page parsed OK, content length: ${parsedContent.textContent.length}, isYouTube: ${parsedContent.isYouTube}")

        if (parsedContent.textContent.isBlank()) {
            return@withContext ExtractionResult.Error("No readable content found at this URL")
        }

        val request = RecipeRequest(
            url = validUrl,
            content = parsedContent.textContent,
            contentType = if (parsedContent.isYouTube) ContentType.YOUTUBE else ContentType.WEBPAGE
        )

        extractRecipeWithQwen(request, parsedContent.title)
    }

    suspend fun extractRecipeWithQwen(
        request: RecipeRequest,
        pageTitle: String? = null,
        retryAttempt: Int = 0
    ): ExtractionResult = withContext(Dispatchers.IO) {
        try {
            // Build prompt based on content type
            val rawPrompt = if (request.contentType == ContentType.YOUTUBE) {
                buildString {
                    appendLine("This is content from a YouTube cooking/recipe video.")
                    appendLine("Source URL: ${request.url}")
                    pageTitle?.let { appendLine("Video title: $it") }
                    appendLine()
                    appendLine("Video information:")
                    append(request.content)
                }
            } else {
                buildString {
                    appendLine("Source URL: ${request.url}")
                    pageTitle?.let { appendLine("Page title: $it") }
                    appendLine()
                    appendLine("Content:")
                    append(request.content)
                }
            }

            // Sanitize content to remove control chars that could cause API errors
            val userPrompt = sanitizeContent(rawPrompt)

            // Enforce minimum delay between API calls
            enforceMinDelay()

            val qwenRequest = QwenRequest(
                model = Constants.QWEN_MODEL,
                input = QwenInput(
                    messages = listOf(
                        QwenMessage(role = "system", content = Constants.SYSTEM_PROMPT.trim()),
                        QwenMessage(role = "user", content = userPrompt)
                    )
                ),
                parameters = QwenParameters(maxTokens = Constants.QWEN_MAX_OUTPUT_TOKENS)
            )

            Timber.d("Qwen API call starting")
            ALog.d("RecipeExtract", "Qwen API call starting, model=${Constants.QWEN_MODEL}")

            // Use Call<ResponseBody> pattern to avoid suspend function ClassCastException with ProGuard
            val call = qwenApiService.generateContent(
                authorization = "Bearer ${BuildConfig.QWEN_API_KEY}",
                request = qwenRequest
            )

            val httpResponse = call.execute()
            ALog.d("RecipeExtract", "Qwen API HTTP response code: ${httpResponse.code()}")

            if (!httpResponse.isSuccessful) {
                val errorBody = httpResponse.errorBody()?.string() ?: "Unknown error"
                ALog.e("RecipeExtract", "HTTP error ${httpResponse.code()}: $errorBody")
                val friendlyMsg = when (httpResponse.code()) {
                    400 -> parseQwenErrorBody(errorBody) ?: "Invalid request. Check the URL."
                    401 -> "Invalid API key. Check your Qwen API key."
                    403 -> "Access denied. Check your Qwen API key."
                    429 -> "Too many requests. Please wait and try again."
                    in 500..599 -> "AI service error. Please try again."
                    else -> "Request failed (HTTP ${httpResponse.code()})"
                }
                return@withContext ExtractionResult.Error(friendlyMsg)
            }

            val responseBody = httpResponse.body()
                ?: return@withContext ExtractionResult.Error("Empty response from AI service")

            val rawJson = responseBody.string()
            ALog.d("RecipeExtract", "Qwen API raw response (${rawJson.length} chars): ${rawJson.take(500)}")

            // Parse JSON manually using JsonParser to avoid Gson ClassCastException
            val rootObj = try {
                JsonParser.parseString(rawJson).asJsonObject
            } catch (parseError: Exception) {
                ALog.e("RecipeExtract", "Failed to parse response JSON: ${parseError.message}")
                return@withContext ExtractionResult.Error("Invalid response from AI service")
            }

            // Check for API-level error codes
            val errorCode = rootObj.get("code")?.takeIf { !it.isJsonNull }?.asString
            if (!errorCode.isNullOrBlank()) {
                val errorMsg = rootObj.get("message")?.takeIf { !it.isJsonNull }?.asString
                    ?: "Qwen API error: $errorCode"
                ALog.e("RecipeExtract", "Qwen API error: code=$errorCode, message=$errorMsg")
                val friendlyMsg = when (errorCode) {
                    "InvalidApiKey", "Unauthorized" -> "Invalid API key. Check your Qwen API key."
                    "Throttling.RateQuota", "Throttling" -> "Too many requests. Please wait and try again."
                    "ServiceUnavailable" -> "AI service temporarily unavailable. Please try again."
                    else -> errorMsg
                }
                return@withContext ExtractionResult.Error(friendlyMsg)
            }

            // Extract text content from response
            val output = rootObj.getAsJsonObject("output")
            if (output == null) {
                return@withContext ExtractionResult.Error("Empty response from AI service")
            }

            // Try output.text first (result_format=text), then output.choices[0].message.content (result_format=message)
            val textContent = output.get("text")?.takeIf { !it.isJsonNull }?.asString?.trim()
                ?: output.getAsJsonArray("choices")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")?.takeIf { !it.isJsonNull }?.asString?.trim()
                ?: return@withContext ExtractionResult.Error("Empty response from AI service")

            Timber.d("Qwen response text length: ${textContent.length}")
            ALog.d("RecipeExtract", "Qwen response text length: ${textContent.length}, first 300 chars: ${textContent.take(300)}")

            val jsonText = extractJsonFromResponse(textContent)
            val extracted = parseExtractedRecipe(jsonText)
                ?: return@withContext ExtractionResult.Error("Could not parse recipe from AI response")

            if (!extracted.error.isNullOrBlank()) {
                return@withContext ExtractionResult.Error(
                    extracted.error ?: "No recipe found in this content"
                )
            }

            val faviconUrl = UrlValidator.getFaviconUrl(request.url)
            val recipe = extracted.toRecipe(request.url, faviconUrl)
                ?: return@withContext ExtractionResult.Error("No recipe found in this content")

            ExtractionResult.Success(recipe)
        } catch (e: HttpException) {
            Timber.e(e, "HTTP error during recipe extraction: code=${e.code()}")
            val errorBody = e.response()?.errorBody()?.string()
            Timber.e("Error body: $errorBody")
            if (e.code() == 429 && retryAttempt < MAX_RETRY_ATTEMPTS) {
                val retryDelay = RETRY_DELAYS[retryAttempt]
                Timber.w("HTTP 429 rate limit, retry ${retryAttempt + 1}/$MAX_RETRY_ATTEMPTS in ${retryDelay}ms")
                delay(retryDelay)
                return@withContext extractRecipeWithQwen(request, pageTitle, retryAttempt + 1)
            }
            val message = when (e.code()) {
                400 -> parseQwenErrorBody(errorBody) ?: "Invalid request. Please check the URL and try again."
                401 -> "Invalid API key. Check your Qwen API key."
                403 -> "Access denied. Check your Qwen API key and permissions."
                429 -> "Too many requests. Please wait a moment and try again."
                500, 503 -> "AI service error. Please try again."
                in 500..599 -> "AI service temporarily unavailable. Please try again."
                else -> "Request failed (HTTP ${e.code()})"
            }
            ExtractionResult.Error(message, e)
        } catch (e: SocketTimeoutException) {
            ExtractionResult.Error("Request took too long. Check your connection and try again.", e)
        } catch (e: IOException) {
            Timber.e(e, "Network error during recipe extraction: ${e.message}")
            mapNetworkError(e, "Network error during extraction")
        } catch (e: Exception) {
            ALog.e("RecipeExtract", "Unexpected error: ${e.javaClass.name}: ${e.message}", e)
            Timber.e(e, "Unexpected error during recipe extraction: ${e.javaClass.simpleName}")
            ExtractionResult.Error("Error: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    private suspend fun enforceMinDelay() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastApiCallTime
        if (elapsed < MIN_TIME_BETWEEN_CALLS) {
            val remainingDelay = MIN_TIME_BETWEEN_CALLS - elapsed
            Timber.d("Throttling: waiting ${remainingDelay}ms before API call")
            delay(remainingDelay)
        }
        lastApiCallTime = System.currentTimeMillis()
    }

    private fun parseExtractedRecipe(jsonText: String): ExtractedRecipeResponse? {
        return try {
            val jsonObject = gson.fromJson(jsonText, JsonObject::class.java) ?: return null

            fun getString(obj: JsonObject, key: String): String? {
                val element = obj.get(key)
                return when {
                    element == null || element.isJsonNull -> null
                    element.isJsonPrimitive -> element.asString
                    else -> element.toString()
                }
            }

            fun getStringList(obj: JsonObject, key: String): List<String>? {
                val element = obj.get(key)
                return when {
                    element == null || element.isJsonNull -> null
                    element.isJsonArray -> element.asJsonArray.mapNotNull { 
                        if (it.isJsonPrimitive) it.asString else it.toString() 
                    }
                    element.isJsonPrimitive && element.asString.isNotBlank() -> 
                        listOf(element.asString)
                    else -> null
                }
            }

            fun getIngredientList(obj: JsonObject, key: String): List<Ingredient>? {
                val element = obj.get(key)
                return when {
                    element == null || element.isJsonNull -> null
                    element.isJsonArray -> element.asJsonArray.mapNotNull { item ->
                        when {
                            item.isJsonObject -> {
                                val name = item.asJsonObject.get("name")?.asString ?: return@mapNotNull null
                                Ingredient(
                                    name = name,
                                    quantity = item.asJsonObject.get("quantity")?.asString,
                                    unit = item.asJsonObject.get("unit")?.asString
                                )
                            }
                            item.isJsonPrimitive -> Ingredient(name = item.asString)
                            else -> null
                        }
                    }
                    element.isJsonPrimitive && element.asString.isNotBlank() -> 
                        listOf(Ingredient(name = element.asString))
                    else -> null
                }
            }

            ExtractedRecipeResponse(
                title = getString(jsonObject, "title"),
                servings = getString(jsonObject, "servings"),
                prepTime = getString(jsonObject, "prepTime"),
                cookTime = getString(jsonObject, "cookTime"),
                ingredients = getIngredientList(jsonObject, "ingredients"),
                instructions = getStringList(jsonObject, "instructions"),
                difficulty = getString(jsonObject, "difficulty")
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse JSON: $jsonText")
            null
        }
    }

    private fun extractJsonFromResponse(text: String): String {
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)
        if (fenced != null) return fenced.groupValues[1].trim()

        val jsonStart = text.indexOf('{')
        val jsonEnd = text.lastIndexOf('}')
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return text.substring(jsonStart, jsonEnd + 1)
        }
        return text
    }

    private fun mapNetworkError(error: Throwable, defaultMessage: String): ExtractionResult.Error {
        val message = when (error) {
            is SocketTimeoutException -> "Request timed out after ${Constants.NETWORK_TIMEOUT_SECONDS} seconds."
            is IOException -> "Network error: ${error.message ?: "Check your internet connection."}"
            else -> "$defaultMessage: ${error.message ?: error.javaClass.simpleName}"
        }
        return ExtractionResult.Error(message, error)
    }

    private fun parseQwenErrorBody(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            // Qwen errors: {"code":"InvalidApiKey","message":"...","request_id":"..."}
            val msgRegex = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"")
            msgRegex.find(errorBody)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Remove control characters and other problematic content that could
     * cause API rejections.
     */
    private fun sanitizeContent(content: String): String {
        return content
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), "")
            .replace('\u00A0', ' ')
            .replace(Regex(" {3,}"), "  ")
            .trim()
    }
}
