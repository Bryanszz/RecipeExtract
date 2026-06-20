package com.recipeextract.app.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Generic HTTP fetch service for raw webpage content when needed outside jsoup.
 */
interface RecipeApiService {

    @GET
    suspend fun fetchPage(@Url url: String): ResponseBody
}
