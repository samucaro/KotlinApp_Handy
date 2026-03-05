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
import com.unibo.handy.service.NetworkService
import com.unibo.handy.ui.navigation.HandyAppEntry
import com.unibo.handy.ui.theme.HandyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // DEFINIZIONE DEL CALLBACK PER I PERMESSI
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Controllia se i permessi sono stati concessi
        val fineLocation = permissions[ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[ACCESS_COARSE_LOCATION] ?: false

        if (fineLocation || coarseLocation) {
            Log.i("HandyMain", "Permessi Posizione concessi. Avvio il Servizio.")
            startHandyService()
        } else {
            Log.w("HandyMain", "Permessi Posizione NEGATI. Il servizio non funzionerà correttamente.")
        }
    }

    // La variabile savedInstanceState salva l'ultimo stato di un Activity dopo la sua distruzione
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HandyTheme {
                HandyAppEntry()
            }
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        // Lista dei permessi necessari
        val permissionsToRequest = mutableListOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(POST_NOTIFICATIONS)
        }

        permissionsToRequest.add(ACCESS_BACKGROUND_LOCATION)

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            Log.i("HandyMain", "Permessi già presenti. Avvio servizio diretto.")
            startHandyService()
        } else {
            Log.i("HandyMain", "Mancano permessi. Richiedo all'utente.")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
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