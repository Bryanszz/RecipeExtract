package com.recipeextract.app.data.remote

import com.recipeextract.app.data.models.QwenRequest
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface QwenApiService {

    @POST("services/aigc/text-generation/generation")
    fun generateContent(
        @Header("Authorization") authorization: String,
        @Body request: QwenRequest
    ): Call<ResponseBody>
}
