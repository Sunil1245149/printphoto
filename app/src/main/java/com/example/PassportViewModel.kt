package com.example

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.FileOutputStream

class PassportViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val client = okhttp3.OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val moshi = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(Constants.BACKEND_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val apiService = retrofit.create(PassportApiService::class.java)

    fun uploadPhoto(context: Context, uris: List<Uri>, layout: String) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val imageParts = uris.mapIndexed { index, uri ->
                    val file = uriToFile(context, uri, "upload_image_$index.jpg")
                    val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("image", file.name, requestFile)
                }
                
                val layoutBody = layout.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = apiService.uploadPhoto(imageParts, layoutBody)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        _uiState.value = UiState.Success
                    } else {
                        val errorMsg = body?.message ?: body?.error ?: "Server reported failure"
                        _uiState.value = UiState.Error(errorMsg)
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    _uiState.value = UiState.Error("Server Error (${response.code()}): $errorBody")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val message = when (e) {
                    is java.net.UnknownHostException -> "Unknown Host: URL invalid or no Internet"
                    is java.net.ConnectException -> "Connection Refused: Server down or URL wrong"
                    is java.net.SocketTimeoutException -> "Timeout: Check connection speed"
                    else -> "${e.javaClass.simpleName}: ${e.message}"
                }
                _uiState.value = UiState.Error("Network Error: $message")
            }
        }
    }

    fun resetState() {
        _uiState.value = UiState.Idle
    }

    private fun uriToFile(context: Context, uri: Uri, fileName: String = "upload_image.jpg"): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.cacheDir, fileName)
        val outputStream = FileOutputStream(file)
        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
    }
}
