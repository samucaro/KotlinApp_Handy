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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.unibo.handy.benchmark.AblationStudyBenchmark
import com.unibo.handy.service.NetworkService
import com.unibo.handy.ui.navigation.HandyAppEntry
import com.unibo.handy.ui.theme.HandyTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Entry point dell'interfaccia utente.
 * Implementa il pattern Single Activity Architecture (SAA) ospitando il grafo di Jetpack Compose.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // DEFINIZIONE DEL CALLBACK PER I PERMESSI (Foreground e Notifiche)
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("HandyMain", "Background Location CONCESSO. L'Heartbeat funzionerà a schermo spento.")
        } else {
            Log.w("HandyMain", "Background Location NEGATO. L'app funzionerà solo se lasciata aperta.")
            Toast.makeText(this, "Senza posizione 'Sempre', riceverai meno richieste di aiuto", Toast.LENGTH_LONG).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Controllia se i permessi sono stati concessi
        val fineLocation = permissions[ACCESS_FINE_LOCATION] ?: false
        val coarseLocation = permissions[ACCESS_COARSE_LOCATION] ?: false

        if (fineLocation || coarseLocation) {
            startHandyService()
            val hasBackgroundLocation = ContextCompat.checkSelfPermission(
                this, ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasBackgroundLocation) {
                // Questa chiamata porterà l'utente nelle Impostazioni di Android
                // dove dovrà selezionare manualmente "Consenti sempre"
                backgroundLocationLauncher.launch(ACCESS_BACKGROUND_LOCATION)
            }
        } else {
            Log.w("HandyMain", "Permessi Posizione NEGATI. Il servizio non funzionerà correttamente.")
        }
    }

    private val requestBackgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) Log.d("HandyMain", "Background Location concessa")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Disegna la UI estendendola dietro le barre di sistema (Status Bar / Navigation Bar)
        enableEdgeToEdge()

        // INNESCO DELL'ABLATION STUDY IN BACKGROUND
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lifecycleScope.launch(Dispatchers.Default) {
                AblationStudyBenchmark.runFullStudy()
            }
        }

        // Inietta il grafo di navigazione Compose come root view
        setContent {
            HandyTheme {
                HandyAppEntry()
            }
        }

        // Verifica lo stato dei permessi all'avvio
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        // Lista dei permessi necessari
        val permissionsToRequest = mutableListOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(POST_NOTIFICATIONS)
        }

        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startHandyService()
            if (ContextCompat.checkSelfPermission(this, ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                requestBackgroundLocationLauncher.launch(ACCESS_BACKGROUND_LOCATION)
            }
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    /**
     * Avvia il NetworkService come Foreground Service.
     * Questo garantisce che l'OS non uccida il processo quando l'app va in background.
     */
    private fun startHandyService() {
        try {
            val intent = Intent(this, NetworkService::class.java)
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e("HandyMain", "Errore critico avvio Service: ${e.message}")
        }
    }
}