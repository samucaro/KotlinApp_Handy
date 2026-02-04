package com.unibo.handy.ui.theme

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import com.unibo.handy.ui.theme.HandyPrimary
import com.unibo.handy.ui.theme.HandySecondary
import com.unibo.handy.ui.theme.HomeVM
import com.unibo.handy.ui.theme.HomeUiState

// --- 1. STRUTTURA PRINCIPALE (SCAFFOLD) ---
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandyAppEntry() {
    val viewModel: HomeVM = viewModel(factory = HomeVM.Factory)
    var selectedTab by remember { mutableIntStateOf(0) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false)
        if (granted) {
            // Permesso concesso!
        }
    }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavBarItem(0, "Home", Icons.Default.Home, selectedTab) { selectedTab = 0 }
                NavBarItem(1, "Attività", Icons.Default.List, selectedTab) { selectedTab = 1 }
                NavBarItem(2, "Chat", Icons.Default.Email, selectedTab) { selectedTab = 2 }
                NavBarItem(3, "Profilo", Icons.Default.Person, selectedTab) { selectedTab = 3 }
            }
        }
    ) { innerPadding ->
        // Contenuto che cambia in base al Tab selezionato
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(Color(0xFFF5F7F8))) {
            when (selectedTab) {
                0 -> HomeScreen(viewModel)
                1 -> PlaceholderScreen("Le tue Attività", Icons.Default.List)
                2 -> PlaceholderScreen("Chat Private", Icons.Default.Email)
                3 -> ProfileScreen(viewModel)
            }
        }
    }
}

// --- 2. HOME SCREEN (IL CUORE) ---
@Composable
fun HomeScreen(viewModel: HomeVM) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = HandyPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("Posizione Attuale", fontSize = 12.sp, color = Color.Gray)
                Text("Bologna, Italia (Simulato)", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.weight(1f))
            // Indicatore ID Utente (Troncato)
            Surface(color = HandyPrimary.copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp)) {
                Text(
                    text = "ID: ${state.userId.take(8)}...",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = HandyPrimary
                )
            }
        }

        // Switch Principale (Modalità)
        ModeSwitchCard(state.isHelperMode) { viewModel.toggleHelperMode(it) }

        Spacer(modifier = Modifier.height(24.dp))

        // Contenuto Dinamico
        if (state.isHelperMode) {
            HelperView()
        } else {
            RequesterView(state, viewModel)
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(state.statusMessage, fontSize = 12.sp, color = Color.Gray)

        // POPUP MATCH TROVATO
        if (state.showMatchSuccess) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissMatchPopup() },
                title = { Text(text = "🎉 Match Trovato!") },
                text = { Text("Abbiamo trovato un Helper compatibile nelle vicinanze proteggendo la tua privacy.") },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissMatchPopup() },
                        colors = ButtonDefaults.buttonColors(containerColor = HandySecondary)
                    ) {
                        Text("Contatta Ora")
                    }
                },
                icon = { Icon(Icons.Filled.Check, contentDescription = null, tint = HandyPrimary) }
            )
        }
    }
}

// --- 3. COMPONENTI UI SPECIFICI ---

@Composable
fun ModeSwitchCard(isHelper: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(if (isHelper) "Modalità Helper" else "Modalità Richiedente", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(if (isHelper) "Sei visibile per lavori" else "Cerca professionisti", fontSize = 14.sp, color = Color.Gray)
            }
            Switch(checked = isHelper, onCheckedChange = onToggle)
        }
    }
}

@Composable
fun HelperView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(40.dp))
        Text("In ascolto...", fontSize = 20.sp, color = HandyPrimary, fontWeight = FontWeight.SemiBold)
        Text("Il sistema sta proteggendo la tua posizione.", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(40.dp))

        // Animazione Radar
        RadarAnimation()
    }
}

@Composable
fun RequesterView(state: HomeUiState, viewModel: HomeVM) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Di cosa hai bisogno?", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        // Categorie (Pulsanti semplici per ora)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip("Idraulico", state.selectedCategory == "Idraulico") { viewModel.updateSearchParameters("Idraulico", state.toleranceRadius) }
            CategoryChip("Elettricista", state.selectedCategory == "Elettricista") { viewModel.updateSearchParameters("Elettricista", state.toleranceRadius) }
            CategoryChip("Medico", state.selectedCategory == "Medico") { viewModel.updateSearchParameters("Medico", state.toleranceRadius) }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Raggio di ricerca: ${state.toleranceRadius.toInt()} km", fontWeight = FontWeight.Bold)
        Slider(
            value = state.toleranceRadius,
            onValueChange = { viewModel.updateSearchParameters(state.selectedCategory, it) },
            valueRange = 1f..50f,
            steps = 4
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.sendHelpRequest() },
            colors = ButtonDefaults.buttonColors(containerColor = HandySecondary),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Trova Aiuto Vicino a Me", fontSize = 18.sp)
        }
    }
}

// --- 4. ACCESSORI GRAFICI ---

@Composable
fun RadarAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        // Cerchio che si espande
        Box(modifier = Modifier
            .size(100.dp)
            .scale(scale)
            .alpha(alpha)
            .clip(CircleShape)
            .background(HandyPrimary.copy(alpha = 0.3f))
        )
        // Punto centrale fisso
        Icon(
            imageVector = Icons.Default.Settings, // Icona ingranaggio o simbolo helper
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(60.dp)
                .background(HandyPrimary, CircleShape)
                .padding(12.dp)
        )
    }
}

@Composable
fun CategoryChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, null) } } else null
    )
}

@Composable
fun RowScope.NavBarItem(index: Int, label: String, icon: ImageVector, selectedIndex: Int, onClick: () -> Unit) {
    NavigationBarItem(
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        selected = index == selectedIndex,
        onClick = onClick,
        colors = NavigationBarItemDefaults.colors(indicatorColor = HandyPrimary.copy(alpha = 0.2f))
    )
}

@Composable
fun PlaceholderScreen(title: String, icon: ImageVector) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(100.dp), tint = Color.LightGray)
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
    }
}

@Composable
fun ProfileScreen(viewModel: HomeVM) {
    val state by viewModel.uiState.collectAsState()
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Il tuo Profilo", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ID Utente:", fontSize = 12.sp, color = Color.Gray)
                Text(state.userId, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Categoria:", fontSize = 12.sp, color = Color.Gray)
                Text(state.selectedCategory, fontWeight = FontWeight.Bold)
            }
        }
    }
}