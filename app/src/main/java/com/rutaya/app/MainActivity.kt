package com.rutaya.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rutaya.app.ui.RutaYaApp
import com.rutaya.app.ui.theme.RutaYaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RutaYaTheme {
                RutaYaApp()
            }
        }
    }
}
