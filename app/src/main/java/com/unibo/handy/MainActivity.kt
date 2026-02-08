package com.unibo.handy

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.unibo.handy.ui.navigation.HandyAppEntry
import com.unibo.handy.ui.theme.HandyTheme

class MainActivity : ComponentActivity() {
    // DEFINIZIONE DEL CALLBACK PER I PERMESSI
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Controllia se i permessi sono stati concessi
        val fineLocationGranted = permissions[ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[ACCESS_COARSE_LOCATION] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[POST_NOTIFICATIONS] ?: false
        } else true

        if (fineLocationGranted || coarseLocationGranted) {
            Log.i("HandyMain", "Permessi Posizione concessi. Avvio il Servizio.")
            startHandyService()
        } else {
            Log.w("HandyMain", "Permessi Posizione NEGATI. Il servizio non funzionerà correttamente.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestPermissions()

        setContent {
            HandyTheme {
                HandyAppEntry()
            }
        }

        /*requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)

        try {
            // Attiva subito il servizio in foreground (mostra notifica) che sarà sempre attivo
            val intent = Intent(this, NetworkService::class.java)
            startForegroundService(intent)
        } catch(e: Exception) {
            Log.e("Handy", "Errore startService: ${e.message}")
        }*/
    }

    private fun checkAndRequestPermissions() {
        // Lista dei permessi necessari
        val permissionsToRequest = mutableListOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(POST_NOTIFICATIONS)
        }

        permissionsToRequest.add(ACCESS_BACKGROUND_LOCATION)

        val permissionsNotGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNotGranted.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsNotGranted.toTypedArray())
        } else {
            startHandyService()
        }
    }

    private fun startHandyService() {
        try {
            val intent = Intent(this, NetworkService::class.java)
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e("HandyMain", "Errore critico avvio Service: ${e.message}")
        }
    }
}