package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private lateinit var voiceManager: VoiceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceManager = VoiceManager(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(voiceManager)
                }
            }
        }
    }

    override fun onDestroy() {
        voiceManager.shutdown()
        super.onDestroy()
    }
}

@Composable
fun MainNavigation(voiceManager: VoiceManager) {
    val navController = rememberNavController()
    val viewModel: PassportViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    var photoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedLanguage by remember { mutableStateOf("English") }

    LaunchedEffect(selectedLanguage) {
        voiceManager.setLanguage(selectedLanguage)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            navController.navigate("camera")
        } else {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                selectedLanguage = selectedLanguage,
                onLanguageChange = { selectedLanguage = it },
                onCaptureClick = {
                    photoUris = emptyList()
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        navController.navigate("camera")
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onGalleryClick = { uri ->
                    photoUris = listOf(uri)
                    navController.navigate("preview")
                }
            )
        }
        composable("camera") {
            CameraScreen(
                onPhotoCaptured = { uri ->
                    photoUris = photoUris + uri
                    navController.navigate("preview")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("preview") {
            if (photoUris.isNotEmpty()) {
                PreviewScreen(
                    photoUris = photoUris,
                    viewModel = viewModel,
                    voiceManager = voiceManager,
                    onAddMore = {
                        navController.navigate("camera")
                    },
                    onUploadSuccess = {
                        Toast.makeText(context, "Photos sent to print!", Toast.LENGTH_LONG).show()
                        viewModel.resetState()
                        photoUris = emptyList()
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    },
                    onBack = {
                        viewModel.resetState()
                        if (photoUris.size > 1) {
                            photoUris = photoUris.dropLast(1)
                        } else {
                            photoUris = emptyList()
                        }
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
