package com.rutaya.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rutaya.app.storage.AppSettings
import com.rutaya.app.storage.Session
import com.rutaya.app.ui.RutaYaApp
import com.rutaya.app.ui.theme.RutaYaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        AppSettings.init(this)
        com.rutaya.app.ui.theme.Pal.systemDark =
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge()
        setContent {
            RutaYaTheme {
                RutaYaApp()
            }
        }
    }
}
