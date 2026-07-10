package com.example

import com.squareup.moshi.JsonClass
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Part

@JsonClass(generateAdapter = true)
data class UploadResponse(
    val success: Boolean,
    val jobId: Long? = null,
    val error: String? = null,
    val message: String? = null
)

interface PassportApiService {
    @Multipart
    @POST("/upload")
    suspend fun uploadPhoto(
        @Part parts: List<MultipartBody.Part>,
        @Part("layout") layout: RequestBody // "4", "8", or "2x4"
    ): Response<okhttp3.ResponseBody>

    @GET("/ping")
    suspend fun ping(): Response<okhttp3.ResponseBody>
}
