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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import android.graphics.Canvas as AndroidCanvas

@Composable
fun EditImageScreen(
    uri: Uri,
    onSave: (Uri) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var brightness by remember { mutableFloatStateOf(1f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    
    // Zoom and Pan
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(uri) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val original = BitmapFactory.decodeStream(inputStream)
                
                // Initial rotation handling from EXIF if needed
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

    val colorMatrix = remember(brightness, contrast) {
        val b = (brightness - 1f) * 255f
        val c = contrast
        val t = (1.0f - c) * 128f
        
        ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, b + t,
            0f, c, 0f, 0f, b + t,
            0f, 0f, c, 0f, b + t,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    val displayMetrics = context.resources.displayMetrics
    val screenWidth = displayMetrics.widthPixels / displayMetrics.density
    val screenHeight = displayMetrics.heightPixels / displayMetrics.density

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
                Text("Edit Photo", fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    val currentBitmap = bitmap ?: return@TextButton
                    
                    // Perform Save
                    val editedUri = saveEditedImage(
                        context, 
                        currentBitmap, 
                        brightness, 
                        contrast, 
                        rotation, 
                        scale, 
                        offset,
                        screenWidth,
                        screenHeight
                    )
                    if (editedUri != null) {
                        onSave(editedUri)
                    }
                }) {
                    Text("Done", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                // Controls
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
                        brightness = 1f
                        contrast = 1f
                        rotation = 0f
                        scale = 1f
                        offset = Offset.Zero
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Brightness", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = 0.5f..1.5f,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Contrast", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = contrast,
                    onValueChange = { contrast = it },
                    valueRange = 0.5f..1.5f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale *= zoom
                        offset += pan
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val screenW = maxWidth
            val screenH = maxHeight

            bitmap?.let { b ->
                val imageBitmap = b.asImageBitmap()
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                            rotationZ = rotation
                        ),
                    colorFilter = ColorFilter.colorMatrix(colorMatrix),
                    contentScale = ContentScale.Fit
                )
            }
            
            // Helpful Guide Overlay (Passport Size Aspect Ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(3.5f / 4.5f)
                    .background(Color.Transparent)
                    .clip(RoundedCornerShape(4.dp))
                    .align(Alignment.Center)
                    .alpha(0.5f)
                    .shadow(0.dp, RoundedCornerShape(4.dp))
            ) {
                // Border guide
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dashPathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 4f
                            pathEffect = dashPathEffect
                        }
                        canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                    }
                }
            }
            
            Text(
                "Pinch to zoom & drag to align",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
            )
        }
    }
}

private fun saveEditedImage(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    brightness: Float,
    contrast: Float,
    rotation: Float,
    scale: Float,
    offset: Offset,
    screenWidth: Float,
    screenHeight: Float
): Uri? {
    try {
        // Create a resulting bitmap that matches the visual state of the guide box.
        // The guide box is 70% width, aspect 3.5:4.5
        val guideWidth = screenWidth * 0.7f
        val guideHeight = guideWidth * (4.5f / 3.5f)
        
        // We want the output to be high res
        val outputScale = 1200f / guideWidth
        val targetWidth = (guideWidth * outputScale).toInt()
        val targetHeight = (guideHeight * outputScale).toInt()
        
        val result = android.graphics.Bitmap.createBitmap(targetWidth, targetHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(result)
        canvas.drawColor(android.graphics.Color.WHITE)
        
        val matrix = Matrix()
        
        val bitmapW = bitmap.width.toFloat()
        val bitmapH = bitmap.height.toFloat()
        
        // Base scale for ContentScale.Fit in the screen
        val fitScale = Math.min(screenWidth / bitmapW, screenHeight / bitmapH)
        
        // Transform logic:
        // 1. Move to center of bitmap
        matrix.postTranslate(-bitmapW / 2f, -bitmapH / 2f)
        // 2. Apply user scale and the fit scale
        matrix.postScale(fitScale * scale, fitScale * scale)
        // 3. Apply rotation
        matrix.postRotate(rotation)
        // 4. Move to screen center + user offset
        matrix.postTranslate(screenWidth / 2f + offset.x, screenHeight / 2f + offset.y)
        
        // 5. Shift relative to the guide box center (which is screen center)
        // Since result canvas (0,0) is guide top-left, we shift by -guide_top_left
        val guideLeft = (screenWidth - guideWidth) / 2f
        val guideTop = (screenHeight - guideHeight) / 2f
        matrix.postTranslate(-guideLeft, -guideTop)
        
        // 6. Scale up to target resolution
        matrix.postScale(outputScale, outputScale)

        val paint = Paint()
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
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        
        canvas.drawBitmap(bitmap, matrix, paint)
        
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
    onPingClick: () -> Unit = {}
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
                            Icon(Icons.Default.Settings, contentDescription = "Merchant Portal", tint = MaterialTheme.colorScheme.primary)
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

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
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
                                onPhotoCaptured(Uri.fromFile(file))
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
    voiceManager: VoiceManager,
    onAddMore: () -> Unit,
    onUploadSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var selectedLayout by remember { mutableStateOf("4") }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState) {
        when (uiState) {
            is PassportViewModel.UiState.Success -> {
                voiceManager.speakStatus("finished")
                onUploadSuccess()
            }
            is PassportViewModel.UiState.Loading -> {
                voiceManager.speakStatus("starting")
            }
            is PassportViewModel.UiState.Error -> {
                voiceManager.speakStatus("error")
            }
            else -> {}
        }
    }

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

        Spacer(modifier = Modifier.height(24.dp))

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
                    if (photoUris.size < 2) {
                        onAddMore()
                    }
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
            OutlinedButton(
                onClick = onAddMore,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Second Photo")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (uiState is PassportViewModel.UiState.Loading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Sending to Print...", fontWeight = FontWeight.Medium)
            }
        } else {
            val canPrint = if (selectedLayout == "2x4") photoUris.size >= 2 else photoUris.isNotEmpty()
            
            Button(
                onClick = { 
                    // Use isCamera=true if all photos are from camera
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
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Send to Print", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
