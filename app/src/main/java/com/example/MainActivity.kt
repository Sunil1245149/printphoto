package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.util.Locale
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

    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("en", "US")
            }
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(onSpeak = { text, lang -> speak(text, lang) })
                }
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun speak(text: String, language: String) {
        val locale = when (language) {
            "Marathi" -> Locale("mr", "IN")
            "Hindi" -> Locale("hi", "IN")
            else -> Locale("en", "US")
        }
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}

@Composable
fun MainNavigation(onSpeak: (String, String) -> Unit) {
    val navController = rememberNavController()
    val viewModel: PassportViewModel = viewModel()
    val geminiViewModel: GeminiViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    var photoUris by remember { mutableStateOf<List<Pair<Uri, Boolean>>>(emptyList()) }
    var selectedLanguage by remember { mutableStateOf("English") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            navController.navigate("camera")
        } else {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(onFinished = {
                navController.navigate("home") {
                    popUpTo("splash") { inclusive = true }
                }
            })
        }
        composable("home") {
            HomeScreen(
                selectedLanguage = selectedLanguage,
                onLanguageChange = { selectedLanguage = it },
                onCaptureClick = {
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
                onPortalClick = {
                    // Portal is now a web portal hosted on Render
                },
                onGalleryClick = { uris ->
                    // For now, edit only the first one if multiple selected, or handle sequentially
                    // To keep it simple, let's take the first one and navigate to edit
                    if (uris.isNotEmpty()) {
                        val encodedUri = Uri.encode(uris.first().toString())
                        navController.navigate("edit_image/$encodedUri")
                    }
                },
                onPingClick = {
                    viewModel.pingServer(context)
                },
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = geminiViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("camera") {
            CameraScreen(
                onPhotoCaptured = { uri ->
                    photoUris = photoUris + (uri to true)
                    navController.navigate("preview") {
                        popUpTo("home") { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("edit_image/{uri}") { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri") ?: ""
            val uri = Uri.parse(uriString)
            EditImageScreen(
                uri = uri,
                geminiViewModel = geminiViewModel,
                onSave = { editedUri ->
                    photoUris = photoUris + (editedUri to false)
                    navController.navigate("preview") {
                        // Pop the edit screen so back from preview doesn't go to edit again easily
                        // Or keep it in backstack if user wants to go back and re-edit? 
                        // Usually better to pop to home/preview.
                        popUpTo("home") { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("preview") {
            if (photoUris.isNotEmpty()) {
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetMultipleContents()
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        val encodedUri = Uri.encode(uris.first().toString())
                        navController.navigate("edit_image/$encodedUri")
                    }
                }

                PreviewScreen(
                    photoUris = photoUris,
                    viewModel = viewModel,
                    geminiViewModel = geminiViewModel,
                    onAddMore = {
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
                    onAddFromGallery = {
                        launcher.launch("image/*")
                    },
                    onUploadSuccess = {
                        val msg = when (selectedLanguage) {
                            "Marathi" -> "फोटो प्रिंटसाठी पाठवले गेले आहेत!"
                            "Hindi" -> "फोटो प्रिंट के लिए भेज दिए गए हैं!"
                            else -> "Photos sent to print!"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        onSpeak(msg, selectedLanguage)
                        
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
