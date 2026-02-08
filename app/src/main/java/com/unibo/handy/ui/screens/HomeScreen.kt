package com.unibo.handy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unibo.handy.ui.components.CategoryChip
import com.unibo.handy.ui.HomeUiState
import com.unibo.handy.ui.HomeVM
import com.unibo.handy.ui.components.ModeSwitchCard
import com.unibo.handy.ui.components.RadarAnimation
import com.unibo.handy.ui.theme.HandyPrimary
import com.unibo.handy.ui.theme.HandySecondary

// Wrapper Stateful
@Composable
fun HomeScreen(viewModel: HomeVM) {
    val state by viewModel.uiState.collectAsState()

    HomeContent(
        state = state,
        onToggleHelperMode = { viewModel.toggleHelperMode(it) },
        onDismissMatchPopup = { viewModel.dismissMatchPopup() },
        onSearchParamUpdate = { cat, radius -> viewModel.updateSearchParameters(cat, radius) },
        onSendHelpRequest = { viewModel.sendHelpRequest() }
    )
}
// Content Stateless (Solo UI)
@Composable
fun HomeContent(
    state: HomeUiState,
    onToggleHelperMode: (Boolean) -> Unit,
    onDismissMatchPopup: () -> Unit,
    onSearchParamUpdate: (String, Float) -> Unit,
    onSendHelpRequest: () -> Unit
) {
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
            Surface(
                color = HandyPrimary.copy(alpha = 0.1f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "ID: ${state.userId.take(8)}...",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = HandyPrimary
                )
            }
        }

        // Switch Principale (Modalità)
        ModeSwitchCard(state.isHelperMode, onToggleHelperMode)

        Spacer(modifier = Modifier.height(24.dp))


        if (state.isHelperMode) {
            HelperView()
        } else {
            RequesterView(
                state = state,
                onCategorySelect = { cat -> onSearchParamUpdate(cat, state.toleranceRadius) },
                onRadiusChange = { rad -> onSearchParamUpdate(state.selectedCategory, rad) },
                onSearchClick = onSendHelpRequest
            )
        }

        Spacer(modifier = Modifier.Companion.weight(1f))
        Text(state.statusMessage, fontSize = 12.sp, color = Color.Gray)

        // POPUP MATCH TROVATO
        if (state.showMatchSuccess) {
            AlertDialog(
                onDismissRequest = onDismissMatchPopup,
                title = {
                    Text(
                        text = "MATCH TROVATO! 🎉",
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text("Un Helper è disponibile vicino a te!")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = state.statusMessage)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onDismissMatchPopup,
                        colors = ButtonDefaults.buttonColors(containerColor = HandySecondary)
                    ) {
                        Text("Contatta Ora") // da modificare
                    }
                },
                icon = { Icon(Icons.Filled.Check, contentDescription = null, tint = HandyPrimary) }
            )
        }
    }
}

@Composable
fun HelperView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            "In ascolto...",
            fontSize = 20.sp,
            color = HandyPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Il sistema sta proteggendo la tua posizione.",
            fontSize = 14.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(40.dp))

        // Animazione Radar
        RadarAnimation()
    }
}

@Composable
fun RequesterView(
    state: HomeUiState,
    onCategorySelect: (String) -> Unit,
    onRadiusChange: (Float) -> Unit,
    onSearchClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Di cosa hai bisogno?",
            fontWeight = FontWeight.Companion.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Categorie (Pulsanti semplici per ora, da introdurre meccannismo migliore)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip(
                "Idraulico",
                state.selectedCategory == "Idraulico"
            ) { onCategorySelect("Idraulico") }
            CategoryChip(
                "Elettricista",
                state.selectedCategory == "Elettricista"
            ) { onCategorySelect("Elettricista") }
            CategoryChip(
                "Medico",
                state.selectedCategory == "Medico"
            ) { onCategorySelect("Medico") }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Raggio di ricerca: ${state.toleranceRadius.toInt()} km",
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = state.toleranceRadius,
            onValueChange = onRadiusChange,
            valueRange = 1f..50f,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSearchClick,
            colors = ButtonDefaults.buttonColors(containerColor = HandySecondary),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Trova Aiuto Vicino a Me", fontSize = 18.sp)
        }
    }
}