package com.example

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                .build()
            chain.proceed(request)
        }
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

    fun uploadPhoto(context: Context, uris: List<Uri>, layout: String, isCamera: Boolean = false) {
        _uiState.value = UiState.Loading
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val imageParts = uris.mapIndexed { index, uri ->
                    val uniqueName = "upload_${System.currentTimeMillis()}_$index.jpg"
                    val file = uriToFile(context, uri, uniqueName, isCamera)
                    if (!file.exists() || file.length() == 0L) {
                        throw Exception("Failed to prepare image for upload: ${file.name}")
                    }
                    val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("image", file.name, requestFile)
                }
                
                val layoutBody = layout.toRequestBody("text/plain".toMediaTypeOrNull())
                val response = apiService.uploadPhoto(imageParts, layoutBody)
                val responseCode = response.code()
                val rawBody = if (response.isSuccessful) response.body()?.string() else response.errorBody()?.string()
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
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
                            _uiState.value = UiState.Error("JSON Parse Error: ${e.message}")
                        }
                    } else {
                        val snippet = if ((rawBody?.length ?: 0) > 200) rawBody?.substring(0, 200) + "..." else rawBody
                        if (snippet?.lowercase()?.contains("<!doctype") == true) {
                            _uiState.value = UiState.Error("Server Error ($responseCode): Received an error page. Check BACKEND_URL.")
                        } else {
                            _uiState.value = UiState.Error("Server Error ($responseCode): ${snippet ?: "No details"}")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.value = UiState.Error("App Error: ${e.message}")
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = UiState.Idle
    }

    fun pingServer(context: android.content.Context) {
        _uiState.value = UiState.Loading
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val response = apiService.ping()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (response.isSuccessful) {
                        _uiState.value = UiState.Idle
                        android.widget.Toast.makeText(context, "Server is ONLINE", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        _uiState.value = UiState.Error("Server offline (${response.code()})")
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.value = UiState.Error("Connection Failed: ${e.message}")
                }
            }
        }
    }

    private fun uriToFile(context: Context, uri: Uri, fileName: String, isCamera: Boolean): File {
        val file = File(context.cacheDir, fileName)
        try {
            if (file.exists()) file.delete()
            var bitmap: Bitmap? = null
            
            // Try to decode with a limit on size to avoid OOM
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            val maxDim = 1024
            var sampleSize = 1
            if (options.outWidth > maxDim || options.outHeight > maxDim) {
                val halfWidth = options.outWidth / 2
                val halfHeight = options.outHeight / 2
                while (halfWidth / sampleSize >= maxDim && halfHeight / sampleSize >= maxDim) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    bitmap = BitmapFactory.decodeStream(input, null, decodeOptions)
                }
            } catch (e: OutOfMemoryError) {
                // Try with even smaller size
                decodeOptions.inSampleSize *= 2
                context.contentResolver.openInputStream(uri)?.use { input ->
                    bitmap = BitmapFactory.decodeStream(input, null, decodeOptions)
                }
            }

            if (bitmap != null) {
                val original = bitmap!!
                // Correct for EXIF rotation
                val rotation = getRotation(uri, context)
                val matrix = android.graphics.Matrix().apply {
                    if (rotation != 0f) preRotate(rotation)
                }

                val oriented = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)

                if (isCamera) {
                    // We need to crop the oriented bitmap based on the 0.65 box width
                    // But we must account for the fact that the camera might be 4:3 while screen is 20:9
                    
                    // Precise cropping based on screen aspect ratio vs image aspect ratio
                    val metrics = context.resources.displayMetrics
                    val screenW = metrics.widthPixels.toFloat()
                    val screenH = metrics.heightPixels.toFloat()
                    
                    val imgW = oriented.width.toFloat()
                    val imgH = oriented.height.toFloat()
                    
                    // Since PreviewView is FILL_CENTER, it scales the image to fill the screen
                    // while maintaining aspect ratio, cropping the excess.
                    // FILL_CENTER means we take the smaller ratio to ensure the other dimension overflows
                    val scale = Math.min(imgW / screenW, imgH / screenH)
                    
                    // Calculate the visible area of the image on the screen
                    val visibleWidth = screenW * scale
                    val visibleHeight = screenH * scale
                    
                    // Offset of the image relative to the screen (centered)
                    val offsetX = (imgW - visibleWidth) / 2f
                    val offsetY = (imgH - visibleHeight) / 2f
                    
                    // Now map the frame coordinates from screen to image
                    val frameWidthPx = screenW * 0.65f
                    val frameHeightPx = frameWidthPx * (4.5f / 3.5f)
                    val frameLeftPx = (screenW - frameWidthPx) / 2f
                    val frameTopPx = (screenH - frameHeightPx) / 3f // 1/3 bias from top
                    
                    val cropLeft = (frameLeftPx * scale + offsetX).toInt()
                    val cropTop = (frameTopPx * scale + offsetY).toInt()
                    val cropWidth = (frameWidthPx * scale).toInt()
                    val cropHeight = (frameHeightPx * scale).toInt()
                    
                    // Ensure we stay within bounds
                    val safeLeft = Math.max(0, Math.min(cropLeft, oriented.width - 1))
                    val safeTop = Math.max(0, Math.min(cropTop, oriented.height - 1))
                    val safeWidth = Math.max(1, Math.min(cropWidth, oriented.width - safeLeft))
                    val safeHeight = Math.max(1, Math.min(cropHeight, oriented.height - safeTop))
                    
                    val cropped = Bitmap.createBitmap(oriented, safeLeft, safeTop, safeWidth, safeHeight)
                    
                    FileOutputStream(file).use { output ->
                        val success = cropped.compress(Bitmap.CompressFormat.JPEG, 95, output) // Higher quality
                        if (!success) throw Exception("Bitmap compression failed")
                        output.flush()
                    }
                    
                    if (cropped != oriented) cropped.recycle()
                    android.util.Log.d("PassportApp", "Image cropped to frame: ${safeWidth}x${safeHeight} at ($safeLeft, $safeTop)")
                } else {
                    // Manual upload: No crop
                    FileOutputStream(file).use { output ->
                        val success = oriented.compress(Bitmap.CompressFormat.JPEG, 95, output)
                        if (!success) throw Exception("Bitmap compression failed")
                        output.flush()
                    }
                    android.util.Log.d("PassportApp", "Manual upload: Image saved as-is with rotation $rotation")
                }
                
                if (oriented != original) oriented.recycle()
                original.recycle()
            } else {
                // Raw copy as last resort
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        val bytes = input.copyTo(output)
                        android.util.Log.d("PassportApp", "Raw copy successful: $bytes bytes")
                    }
                }
            }
            if (!file.exists() || file.length() == 0L) {
                throw Exception("Resulting file is empty or missing")
            }
        } catch (e: Exception) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e2: Exception) { }
        }
        return file
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
    }

    private fun getRotation(uri: Uri, context: Context): Float {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exifInterface = androidx.exifinterface.media.ExifInterface(input)
                when (exifInterface.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
    }
}
