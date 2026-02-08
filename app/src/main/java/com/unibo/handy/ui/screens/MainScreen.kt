package com.unibo.handy.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.unibo.handy.ui.HomeVM
import com.unibo.handy.ui.components.NavBarItem

@Composable
fun MainScreen(
    viewModel: HomeVM,
    onOpenChat: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    // Gestione Permessi
    val permissionsToRequest = remember {
        mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    // Chiede i permessi alla prima apertura dell'app
    LaunchedEffect(Unit) {
        launcher.launch(permissionsToRequest)
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavBarItem(0, "Home", Icons.Default.Home, selectedTab) { selectedTab = 0 }
                NavBarItem(
                    1,
                    "Attività",
                    Icons.AutoMirrored.Filled.List,
                    selectedTab
                ) { selectedTab = 1 }
                NavBarItem(2, "Chat", Icons.Default.Sms, selectedTab) { selectedTab = 2 }
                NavBarItem(3, "Profilo", Icons.Default.Person, selectedTab) { selectedTab = 3 }
            }
        }
    ) { innerPadding ->
        // Contenuto che cambia in base al Tab selezionato
        Box(
            modifier = Modifier.padding(innerPadding).fillMaxSize()
                .background(Color(0xFFF5F7F8))
        ) {
            when (selectedTab) {
                0 -> HomeScreen(viewModel)
                1 -> ActivityScreen(viewModel = viewModel, onChatClick = onOpenChat)
                2 -> ChatScreen(viewModel = viewModel, onChatClick = onOpenChat)
                3 -> ProfileScreen(viewModel)
            }
        }
    }
}