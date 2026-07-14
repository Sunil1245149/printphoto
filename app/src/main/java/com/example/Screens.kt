package com.example

import com.example.R
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import android.graphics.BitmapFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.ClipOp
import kotlinx.coroutines.launch

@Composable
fun EditImageScreen(
    uri: Uri,
    geminiViewModel: GeminiViewModel,
    onSave: (Uri) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var brightness by remember { mutableFloatStateOf(1f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var showFilters by remember { mutableStateOf(false) }
    var selectedBgColor by remember { mutableIntStateOf(android.graphics.Color.WHITE) }
    val scope = rememberCoroutineScope()
    
    val isEnhancing = geminiViewModel.isEnhancing
    val enhancementError = geminiViewModel.enhancementError
    
    // Crop selection area (fractions 0f..1f of the displayed image)
    var cropLeft by remember { mutableFloatStateOf(0.175f) }
    var cropTop by remember { mutableFloatStateOf(0.08f) }
    var cropRight by remember { mutableFloatStateOf(0.825f) }
    var cropBottom by remember { mutableFloatStateOf(0.915f) }
    
    val displayMetrics = context.resources.displayMetrics

    LaunchedEffect(key1 = uri) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val original = BitmapFactory.decodeStream(inputStream)
                
                val exifRotation = try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val exifInterface = androidx.exifinterface.media.ExifInterface(input)
                        when (exifInterface.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)) {
                            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }
                    } ?: 0f
                } catch (e: Exception) { 0f }

                if (exifRotation != 0f) {
                    val matrix = Matrix().apply { postRotate(exifRotation) }
                    bitmap = android.graphics.Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
                } else {
                    bitmap = original
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val colorMatrix = remember(brightness, contrast, saturation) {
        val b = (brightness - 1f) * 255f
        val c = contrast
        val t = (1.0f - c) * 128f
        val cm = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, b + t,
            0f, c, 0f, 0f, b + t,
            0f, 0f, c, 0f, b + t,
            0f, 0f, 0f, 1f, 0f
        ))
        val saturationMatrix = androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(saturation) }
        cm.timesAssign(saturationMatrix)
        cm
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
                Text("Crop Photo", fontWeight = FontWeight.Bold)
                Button(onClick = {
                    val currentBitmap = bitmap ?: return@Button
                    
                    val bitmapW = if (rotation % 180f == 0f) currentBitmap.width.toFloat() else currentBitmap.height.toFloat()
                    val bitmapH = if (rotation % 180f == 0f) currentBitmap.height.toFloat() else currentBitmap.width.toFloat()
                    
                    val containerW = displayMetrics.widthPixels.toFloat()
                    val containerH = (displayMetrics.heightPixels - 350 * density.density) 

                    val scale = Math.min(containerW / bitmapW, containerH / bitmapH)
                    val displayedW = bitmapW * scale
                    val displayedH = bitmapH * scale

                    val editedUri = saveEditedImageWithCrop(
                        context, 
                        currentBitmap, 
                        cropLeft, cropTop, cropRight, cropBottom,
                        brightness, 
                        contrast, 
                        saturation,
                        rotation
                    )
                    if (editedUri != null) {
                        onSave(editedUri)
                    }
                }) {
                    Text("DONE", fontWeight = FontWeight.ExtraBold)
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { rotation = (rotation - 90f) % 360f }) {
                        Icon(Icons.Default.RotateLeft, contentDescription = "Rotate Left")
                    }
                    IconButton(onClick = { rotation = (rotation + 90f) % 360f }) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate Right")
                    }
                    IconButton(onClick = {
                    showFilters = !showFilters
                }) {
                    Icon(
                        imageVector = if (showFilters) Icons.Default.Check else Icons.Default.Settings,
                        contentDescription = "Toggle Filters"
                    )
                }

                IconButton(onClick = {
                    brightness = 1f
                    contrast = 1f
                    saturation = 1f
                    rotation = 0f
                    cropLeft = 0.175f
                    cropTop = 0.08f
                    cropRight = 0.825f
                    cropBottom = 0.915f
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset")
                }

                // Magic Features Group
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // On-device Magic HD Auto-Fix (Free & Unlimited)
                    IconButton(
                        onClick = {
                            scope.launch {
                                val resultUri = geminiViewModel.autoEnhanceOnDevice(context, uri)
                                if (resultUri != null) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val inputStream = context.contentResolver.openInputStream(resultUri)
                                            bitmap = BitmapFactory.decodeStream(inputStream)
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                }
                            }
                        },
                        enabled = !geminiViewModel.isProcessingDevice
                    ) {
                        Icon(
                            Icons.Default.AutoFixNormal,
                            contentDescription = "Magic HD Fix",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // On-device Background Removal (Free & Unlimited)
                    IconButton(
                        onClick = {
                            scope.launch {
                                val resultUri = geminiViewModel.removeBackgroundOnDevice(context, uri, selectedBgColor)
                                if (resultUri != null) {
                                    // Reload bitmap
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val inputStream = context.contentResolver.openInputStream(resultUri)
                                            bitmap = BitmapFactory.decodeStream(inputStream)
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                }
                            }
                        },
                        enabled = !geminiViewModel.isProcessingDevice
                    ) {
                        if (geminiViewModel.isProcessingDevice) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.Portrait,
                                contentDescription = "Remove BG",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        scope.launch {
                            geminiViewModel.enhanceImage(context, uri)
                        }
                    },
                    enabled = !isEnhancing
                ) {
                    if (isEnhancing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Default.AutoFixHigh,
                            contentDescription = "AI Enhance (Gemini)",
                            tint = if (geminiViewModel.apiKey.isNotBlank()) MaterialTheme.colorScheme.tertiary else Color.Gray
                        )
                    }
                }
            }
            
            if (enhancementError != null) {
                Text(
                    text = enhancementError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("BG Color:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp))
                val bgColors = listOf(
                    android.graphics.Color.WHITE,
                    android.graphics.Color.parseColor("#ADD8E6"), // Light Blue
                    android.graphics.Color.parseColor("#4169E1"), // Royal Blue
                    android.graphics.Color.parseColor("#000080"), // Navy
                    android.graphics.Color.parseColor("#FF0000"), // Red
                    android.graphics.Color.parseColor("#008000"), // Green
                    android.graphics.Color.parseColor("#FFFF00"), // Yellow
                    android.graphics.Color.parseColor("#FFA500"), // Orange
                    android.graphics.Color.parseColor("#800080"), // Purple
                    android.graphics.Color.parseColor("#FFC0CB"), // Pink
                    android.graphics.Color.parseColor("#000000"), // Black
                    android.graphics.Color.LTGRAY
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(bgColors) { colorInt ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(androidx.compose.ui.graphics.Color(colorInt), CircleShape)
                                .border(
                                    width = if (selectedBgColor == colorInt) 2.dp else 1.dp,
                                    color = if (selectedBgColor == colorInt) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .clickable { selectedBgColor = colorInt }
                        )
                    }
                }
            }

            if (showFilters) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Bright", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(50.dp))
                    Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0.5f..1.5f, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Contrast", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(50.dp))
                    Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.5f..1.5f, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Colors", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(50.dp))
                    Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..2f, modifier = Modifier.weight(1f))
                }
            } else {
                Text(
                    "Select crop area and press 'Done'",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = maxWidth
            val containerHeight = maxHeight
            
            bitmap?.let { b ->
                val imageBitmap = remember(b, rotation) {
                    if (rotation == 0f) b.asImageBitmap()
                    else {
                        val matrix = Matrix().apply { postRotate(rotation) }
                        android.graphics.Bitmap.createBitmap(b, 0, 0, b.width, b.height, matrix, true).asImageBitmap()
                    }
                }
                
                val bitmapW = imageBitmap.width.toFloat()
                val bitmapH = imageBitmap.height.toFloat()
                val containerW = containerWidth.value * density.density
                val containerH = containerHeight.value * density.density
                
                val scale = Math.min(containerW / bitmapW, containerH / bitmapH)
                val displayedW_dp = (bitmapW * scale / density.density).dp
                val displayedH_dp = (bitmapH * scale / density.density).dp
                
                Box(modifier = Modifier.size(displayedW_dp, displayedH_dp)) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        colorFilter = ColorFilter.colorMatrix(colorMatrix),
                        contentScale = ContentScale.Fit
                    )
                    
                    // Selection Box
                    Canvas(modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val dx = dragAmount.x / (displayedW_dp.value * density.density)
                                val dy = dragAmount.y / (displayedH_dp.value * density.density)
                                
                                val w = cropRight - cropLeft
                                val h = cropBottom - cropTop
                                
                                cropLeft = (cropLeft + dx).coerceIn(0f, 1f - w)
                                cropTop = (cropTop + dy).coerceIn(0f, 1f - h)
                                cropRight = cropLeft + w
                                cropBottom = cropTop + h
                            }
                        }
                    ) {
                        val w = size.width
                        val h = size.height
                        
                        // Overlay Dimming
                        drawRect(Color.Black.copy(alpha = 0.5f))
                        
                        // Clear Selection Area
                        withTransform({
                            val rectW = (cropRight - cropLeft) * w
                            val rectH = (cropBottom - cropTop) * h
                            clipRect(
                                left = cropLeft * w,
                                top = cropTop * h,
                                right = cropRight * w,
                                bottom = cropBottom * h,
                                clipOp = androidx.compose.ui.graphics.ClipOp.Difference
                            )
                        }) {
                            drawRect(Color.Transparent)
                        }

                        // White Border
                        val rectLeft = cropLeft * w
                        val rectTop = cropTop * h
                        val rectRight = cropRight * w
                        val rectBottom = cropBottom * h
                        
                        drawIntoCanvas { canvas ->
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = 10f // Thicker border
                            }
                            canvas.nativeCanvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, paint)
                            
                            // Corners - Even bigger handles
                            val handleSize = 60f
                            paint.style = android.graphics.Paint.Style.FILL
                            canvas.nativeCanvas.drawCircle(rectLeft, rectTop, handleSize, paint)
                            canvas.nativeCanvas.drawCircle(rectRight, rectTop, handleSize, paint)
                            canvas.nativeCanvas.drawCircle(rectLeft, rectBottom, handleSize, paint)
                            canvas.nativeCanvas.drawCircle(rectRight, rectBottom, handleSize, paint)
                        }
                    }
                    
                    // Invisible Corner Handles for Dragging - Extra large touch area
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Top Left Handle
                        Box(modifier = Modifier
                            .offset((cropLeft * displayedW_dp.value).dp - 40.dp, (cropTop * displayedH_dp.value).dp - 40.dp)
                            .size(80.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    cropLeft = (cropLeft + dragAmount.x / (displayedW_dp.value * density.density)).coerceIn(0f, cropRight - 0.05f)
                                    cropTop = (cropTop + dragAmount.y / (displayedH_dp.value * density.density)).coerceIn(0f, cropBottom - 0.05f)
                                }
                            }
                        )
                        // Bottom Right Handle
                        Box(modifier = Modifier
                            .offset((cropRight * displayedW_dp.value).dp - 40.dp, (cropBottom * displayedH_dp.value).dp - 40.dp)
                            .size(80.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    cropRight = (cropRight + dragAmount.x / (displayedW_dp.value * density.density)).coerceIn(cropLeft + 0.05f, 1f)
                                    cropBottom = (cropBottom + dragAmount.y / (displayedH_dp.value * density.density)).coerceIn(cropTop + 0.05f, 1f)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun cropCameraImage(context: android.content.Context, uri: Uri, viewWidth: Float, viewHeight: Float): Uri? {
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        
        if (originalBitmap == null) return null

        // EXIF Rotation
        val exif = context.contentResolver.openInputStream(uri)?.use { 
            androidx.exifinterface.media.ExifInterface(it)
        }
        val rotationDegrees = when (exif?.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            android.graphics.Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
        } else {
            originalBitmap
        }
        
        val bitmapW = rotatedBitmap.width.toFloat()
        val bitmapH = rotatedBitmap.height.toFloat()
        
        // Use the actual view dimensions passed from the UI
        val screenW = viewWidth
        val screenH = viewHeight
        
        // We know the viewfinder is 65% of the width and at 1/3 height
        // We need to map this screen area to the bitmap pixels, considering CameraX FILL_CENTER
        
        val screenRatio = screenW / screenH
        val bitmapRatio = bitmapW / bitmapH
        
        var scale: Float
        var dx = 0f
        var dy = 0f
        
        if (bitmapRatio > screenRatio) {
            // Bitmap is wider than screen
            scale = screenH / bitmapH
            dx = (bitmapW * scale - screenW) / 2f
        } else {
            // Bitmap is taller than screen
            scale = screenW / bitmapW
            dy = (bitmapH * scale - screenH) / 2f
        }
        
        // Viewfinder in screen pixels (from PassportOverlay logic)
        val vw = screenW * 0.65f
        val vh = vw * (4.5f / 3.5f)
        val vl = (screenW - vw) / 2f
        val vt = (screenH - vh) / 3f // Matches the 1/3 top offset in Overlay
        
        // Map screen viewfinder to bitmap pixels
        // We use the same FILL_CENTER logic that PreviewView uses
        val cropL = (vl + dx) / scale
        val cropT = (vt + dy) / scale
        val cropW = vw / scale
        val cropH = vh / scale
        
        val cropped = android.graphics.Bitmap.createBitmap(
            rotatedBitmap,
            cropL.toInt().coerceIn(0, (rotatedBitmap.width - 1).coerceAtLeast(0)),
            cropT.toInt().coerceIn(0, (rotatedBitmap.height - 1).coerceAtLeast(0)),
            cropW.toInt().coerceIn(1, (rotatedBitmap.width - cropL.toInt()).coerceAtLeast(1)),
            cropH.toInt().coerceIn(1, (rotatedBitmap.height - cropT.toInt()).coerceAtLeast(1))
        )
        
        val file = File(context.cacheDir, "camera_crop_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
        }
        return Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

private fun saveEditedImageWithCrop(
    context: android.content.Context,
    originalBitmap: android.graphics.Bitmap,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    rotation: Float
): Uri? {
    try {
        val rotatedBitmap = if (rotation == 0f) originalBitmap else {
            val matrix = Matrix().apply { postRotate(rotation) }
            android.graphics.Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
        }
        
        val bitmapW = rotatedBitmap.width.toFloat()
        val bitmapH = rotatedBitmap.height.toFloat()
        
        val leftPx = cropLeft * bitmapW
        val topPx = cropTop * bitmapH
        val widthPx = (cropRight - cropLeft) * bitmapW
        val heightPx = (cropBottom - cropTop) * bitmapH
        
        val cropped = android.graphics.Bitmap.createBitmap(
            rotatedBitmap, 
            leftPx.toInt().coerceIn(0, bitmapW.toInt() - 1), 
            topPx.toInt().coerceIn(0, bitmapH.toInt() - 1), 
            widthPx.toInt().coerceIn(1, bitmapW.toInt() - leftPx.toInt()), 
            heightPx.toInt().coerceIn(1, bitmapH.toInt() - topPx.toInt())
        )
        
        val targetWidth = 1200
        val targetHeight = (targetWidth * (4.5f / 3.5f)).toInt()
        val result = android.graphics.Bitmap.createBitmap(targetWidth, targetHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(result)
        
        val paint = AndroidPaint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            val cm = android.graphics.ColorMatrix()
            val b = (brightness - 1f) * 255f
            val c = contrast
            val t = (1.0f - c) * 128f
            cm.set(floatArrayOf(
                c, 0f, 0f, 0f, b + t,
                0f, c, 0f, 0f, b + t,
                0f, 0f, c, 0f, b + t,
                0f, 0f, 0f, 1f, 0f
            ))
            val satMatrix = android.graphics.ColorMatrix()
            satMatrix.setSaturation(saturation)
            cm.postConcat(satMatrix)
            colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        }
        
        canvas.drawBitmap(cropped, null, android.graphics.Rect(0, 0, targetWidth, targetHeight), paint)
        
        val file = File(context.cacheDir, "edited_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            result.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
        }
        return Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        onFinished()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Splash Background
        Image(
            painter = painterResource(id = R.drawable.img_splash_bg_1783778127001),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Overlay with Logo
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .size(140.dp)
                    .shadow(12.dp, CircleShape),
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_app_icon_asset_1783778112250),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Passport Pro",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "Studio Quality in Your Pocket",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun HomeScreen(
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    onCaptureClick: () -> Unit,
    onPortalClick: () -> Unit,
    onGalleryClick: (List<Uri>) -> Unit,
    onPingClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onGalleryClick(uris)
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_app_icon_asset_1783778112250),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Text(
                            text = "Passport Pro",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPortalClick) {
                            Icon(Icons.Default.Language, contentDescription = "Portal", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(bottom = 32.dp, top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onCaptureClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Start Capture", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { launcher.launch("image/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Select from Gallery")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LanguageSelector(
                selectedLanguage = selectedLanguage,
                onLanguageChange = onLanguageChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            DashboardCard(
                title = "Passport Capture",
                subtitle = "Take new photo",
                icon = Icons.Default.CameraAlt,
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = onCaptureClick,
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )
            
            DashboardCard(
                title = "Upload Photo",
                subtitle = "Use gallery",
                icon = Icons.Default.PhotoLibrary,
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { launcher.launch("image/*") },
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )

            TextButton(onClick = onPingClick) {
                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Check Server Connection")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Printing Tips", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    LayoutItem(Icons.Default.Lightbulb, "Good Lighting", "Ensure face is evenly lit")
                    LayoutItem(Icons.Default.Straighten, "Alignment", "Keep eyes within the guide")
                    LayoutItem(Icons.Default.Print, "Ready to Print", "Layouts are chosen after capture")
                }
            }
        }
    }
}

@Composable
fun LayoutItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CameraScreen(
    onPhotoCaptured: (Uri) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }
    
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewWidth = with(LocalDensity.current) { maxWidth.toPx() }
        val viewHeight = with(LocalDensity.current) { maxHeight.toPx() }

        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = androidx.camera.core.Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    try {
                        cameraProvider.unbindAll()
                        val cameraInstance = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                        camera = cameraInstance
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        PassportOverlay(modifier = Modifier.fillMaxSize())

        // Top Controls (Flash and Back)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            IconButton(
                onClick = {
                    torchEnabled = !torchEnabled
                    camera?.cameraControl?.enableTorch(torchEnabled)
                },
                modifier = Modifier
                    .background(if (torchEnabled) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Torch",
                    tint = Color.White
                )
            }
        }

        // Zoom Control (Slider above capture button)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp, start = 60.dp, end = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Zoom: ${"%.1f".format(zoomRatio)}x",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Slider(
                value = zoomRatio,
                onValueChange = {
                    zoomRatio = it
                    camera?.cameraControl?.setZoomRatio(it)
                },
                valueRange = 1f..5f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
        }

        // Capture Button
        FloatingActionButton(
            onClick = {
                val file = File(context.cacheDir, "${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

                imageCapture.takePicture(
                    outputOptions,
                    cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            ContextCompat.getMainExecutor(context).execute {
                                val savedUri = Uri.fromFile(file)
                                val croppedUri = cropCameraImage(context, savedUri, viewWidth, viewHeight)
                                onPhotoCaptured(croppedUri ?: savedUri)
                            }
                        }

                        override fun onError(exc: ImageCaptureException) {
                            exc.printStackTrace()
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .size(72.dp),
            containerColor = Color.White,
            contentColor = Color.Black,
            shape = CircleShape
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Capture", modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
fun PreviewScreen(
    photoUris: List<Pair<Uri, Boolean>>,
    viewModel: PassportViewModel,
    geminiViewModel: GeminiViewModel,
    onAddMore: () -> Unit,
    onAddFromGallery: () -> Unit,
    onUploadSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var selectedLayout by remember { mutableStateOf("4") }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val price = when (selectedLayout) {
        "4" -> "25"
        "8" -> "50"
        "2x4" -> "50"
        else -> "50"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 8.dp,
                shadowElevation = 4.dp
            ) {
                if (photoUris.size > 1) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        photoUris.take(2).forEach { (uri, _) ->
                            AsyncImage(
                                model = uri,
                                contentDescription = "Selected Photo",
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                } else {
                    AsyncImage(
                        model = photoUris.firstOrNull()?.first,
                        contentDescription = "Selected Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Price: ₹$price",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Select Print Layout", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Standard size 3.5x4.5cm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LayoutOption(
                    label = "4 Pcs",
                    icon = Icons.Default.Grid4x4,
                    isSelected = selectedLayout == "4",
                    onClick = { selectedLayout = "4" },
                    modifier = Modifier.weight(1f)
                )
                LayoutOption(
                    label = "8 Pcs",
                    icon = Icons.Default.GridView,
                    isSelected = selectedLayout == "8",
                    onClick = { selectedLayout = "8" },
                    modifier = Modifier.weight(1f)
                )
                LayoutOption(
                    label = "Mix",
                    icon = Icons.Default.LibraryAdd,
                    isSelected = selectedLayout == "2x4",
                    onClick = { 
                        selectedLayout = "2x4"
                    },
                    modifier = Modifier.weight(1f)
                )
                LayoutOption(
                    label = "Full",
                    icon = Icons.Default.FilterFrames,
                    isSelected = selectedLayout == "Single",
                    onClick = { selectedLayout = "Single" },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedLayout == "2x4" && photoUris.size < 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddMore,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Capture 2nd", fontSize = 12.sp)
                    }
                    
                    OutlinedButton(
                        onClick = onAddFromGallery,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upload 2nd", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            val canPrint = if (selectedLayout == "2x4") photoUris.size >= 2 else photoUris.isNotEmpty()
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Cloud Print/Upload Button
                if (uiState is PassportViewModel.UiState.Loading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Sending to Merchant Portal...", fontWeight = FontWeight.Medium)
                    }
                } else {
                    Button(
                        onClick = { 
                            val isAnyFromCamera = photoUris.any { it.second }
                            viewModel.uploadPhoto(
                                context = context, 
                                uris = photoUris.map { it.first }, 
                                layout = selectedLayout,
                                isCamera = isAnyFromCamera
                            ) 
                        },
                        enabled = canPrint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Send to Merchant Portal", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (uiState is PassportViewModel.UiState.Error) {
                Surface(
                    modifier = Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = (uiState as PassportViewModel.UiState.Error).message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Text("Go Back", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LayoutOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = if (isSelected) 8.dp else 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = label,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                )
            )
        }
    }
}

@Composable
fun DashboardCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.Black.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
            Column {
                Text(title, fontWeight = FontWeight.ExtraBold, color = Color.Black, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.Black.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun LanguageSelector(
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    val languages = listOf("English", "Hindi", "Marathi")
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Voice Assistant Language",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(4.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            languages.forEach { lang ->
                val isSelected = selectedLanguage == lang
                Surface(
                    onClick = { onLanguageChange(lang) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = if (isSelected) 4.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (lang == "Hindi") "हिन्दी" else if (lang == "Marathi") "मराठी" else "English",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}
