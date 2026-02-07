package com.unibo.handy

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import com.unibo.handy.ui.theme.HandyAppEntry
import com.unibo.handy.ui.theme.HandyTheme

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)

        try {
            // Attiva subito il servizio in foreground (mostra notifica) che sarà sempre attivo
            val intent = Intent(this, NetworkService::class.java)
            startForegroundService(intent)
        } catch(e: Exception) {
            Log.e("Handy", "Errore startService: ${e.message}")
        }

        enableEdgeToEdge()
        setContent {
            HandyTheme {
                HandyAppEntry()
            }
        }
    }
}