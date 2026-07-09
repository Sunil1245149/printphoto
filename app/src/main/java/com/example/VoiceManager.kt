package com.example

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var currentLanguage = "English"

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                setLanguage(currentLanguage)
            }
        }
    }

    fun setLanguage(language: String) {
        currentLanguage = language
        val locale = when (language) {
            "Hindi" -> Locale("hi", "IN")
            "Marathi" -> Locale("mr", "IN")
            else -> Locale.US
        }
        tts?.language = locale
    }

    fun speak(text: String, hindi: String, marathi: String) {
        if (!isReady) return
        
        val message = when (currentLanguage) {
            "Hindi" -> hindi
            "Marathi" -> marathi
            else -> text
        }
        
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun speakStatus(status: String) {
        when (status) {
            "starting" -> speak(
                "Starting print process",
                "प्रिंट प्रक्रिया शुरू हो रही है",
                "प्रिंट प्रक्रिया सुरू होत आहे"
            )
            "processing" -> speak(
                "Processing your photo",
                "आपकी फोटो पर काम चल रहा है",
                "तुमच्या फोटोवर प्रक्रिया सुरू आहे"
            )
            "finished" -> speak(
                "Print finished successfully",
                "प्रिंट सफलतापूर्वक पूरा हो गया",
                "प्रिंट यशस्वीरित्या पूर्ण झाले"
            )
            "error" -> speak(
                "Error occurred during printing",
                "प्रिंटिंग के दौरान त्रुटि हुई",
                "प्रिंटिंग दरम्यान त्रुटी आली"
            )
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
