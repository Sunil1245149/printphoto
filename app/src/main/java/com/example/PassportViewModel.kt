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
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
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
                val responseCode = response.code()
                val rawBody = if (response.isSuccessful) response.body()?.string() else response.errorBody()?.string()
                
                if (response.isSuccessful && rawBody != null) {
                    try {
                        val adapter = moshi.adapter(UploadResponse::class.java)
                        val uploadResponse = adapter.fromJson(rawBody)
                        if (uploadResponse?.success == true) {
                            _uiState.value = UiState.Success
                        } else {
                            val errorMsg = uploadResponse?.message ?: uploadResponse?.error ?: "Server reported failure"
                            _uiState.value = UiState.Error("Server: $errorMsg")
                        }
                    } catch (e: Exception) {
                        val snippet = if (rawBody.length > 200) rawBody.substring(0, 200) + "..." else rawBody
                        _uiState.value = UiState.Error("JSON Parse Error. Server sent: $snippet")
                    }
                } else {
                    val snippet = if ((rawBody?.length ?: 0) > 200) rawBody?.substring(0, 200) + "..." else rawBody
                    _uiState.value = UiState.Error("Server Error ($responseCode): ${snippet ?: "No details"}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val message = when {
                    e is java.net.UnknownHostException -> "Internet problem or wrong URL"
                    e is java.net.ConnectException -> "Cannot connect to server"
                    e is java.net.SocketTimeoutException -> "Server taking too long (Timeout)"
                    e.message?.contains("Json") == true -> "Server returned malformed data (likely HTML error page)"
                    else -> "${e.javaClass.simpleName}: ${e.message}"
                }
                _uiState.value = UiState.Error("Network Error: $message")
            }
        }
    }

    fun resetState() {
        _uiState.value = UiState.Idle
    }

    fun pingServer(context: android.content.Context) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val response = apiService.ping()
                if (response.isSuccessful) {
                    _uiState.value = UiState.Idle
                    android.widget.Toast.makeText(context, "Server is ONLINE", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    _uiState.value = UiState.Error("Server offline (${response.code()})")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Connection Failed: ${e.message}")
            }
        }
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
