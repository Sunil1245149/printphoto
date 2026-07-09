package com.example

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface PassportApiService {
    @Multipart
    @POST("upload")
    suspend fun uploadPhoto(
        @Part images: List<MultipartBody.Part>,
        @Part("layout") layout: RequestBody // "4", "8", or "2x4"
    ): Response<Unit>
}
