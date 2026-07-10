package com.example

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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun HomeScreen(
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    onCaptureClick: () -> Unit,
    onPortalClick: () -> Unit,
    onGalleryClick: (Uri) -> Unit,
    onPingClick: () -> Unit = {}
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onGalleryClick(it) }
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
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Text(
                            text = "PassportPrint Pro",
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
                subtitle = "Standard 3.5x4.5cm photos",
                icon = Icons.Default.CameraAlt,
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = onCaptureClick,
                modifier = Modifier.fillMaxWidth()
            )
            
            DashboardCard(
                title = "Print from Files",
                subtitle = "Use your existing photos",
                icon = Icons.Default.PhotoLibrary,
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { launcher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
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

        // Zoom Slider (Above Capture Button)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 150.dp, start = 48.dp, end = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Zoom: ${"%.1f".format(zoomRatio)}x",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.background(Color.Black.copy(0.4f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp)
            )
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
    photoUris: List<Uri>,
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
                    photoUris.take(2).forEach { uri ->
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
                    model = photoUris.firstOrNull(),
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LayoutOption(
                label = "4 Photos",
                icon = Icons.Default.Grid4x4,
                isSelected = selectedLayout == "4",
                onClick = { selectedLayout = "4" },
                modifier = Modifier.weight(1f)
            )
            LayoutOption(
                label = "8 Photos",
                icon = Icons.Default.GridView,
                isSelected = selectedLayout == "8",
                onClick = { selectedLayout = "8" },
                modifier = Modifier.weight(1f)
            )
            LayoutOption(
                label = "4+4 Mix",
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
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedLayout == "2x4" && photoUris.size < 2) {
            OutlinedButton(
                onClick = onAddMore,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Capture Second Photo")
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
                onClick = { viewModel.uploadPhoto(context, photoUris, selectedLayout) },
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
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(16.dp),
        color = color
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.Black.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = Color.Black, style = MaterialTheme.typography.bodyMedium)
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
