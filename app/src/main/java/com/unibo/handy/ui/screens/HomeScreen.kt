package com.unibo.handy.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unibo.handy.domain.model.User
import com.unibo.handy.ui.components.CategoryChip
import com.unibo.handy.ui.MatchUiState
import com.unibo.handy.ui.components.ModeSwitchCard
import com.unibo.handy.ui.components.RadarAnimation
import com.unibo.handy.ui.theme.HandyPrimary
import com.unibo.handy.ui.theme.HandySecondary

// Wrapper Stateful
@Composable
fun HomeScreen(
    currentUser: User?,
    isHelperMode: Boolean,
    matchState: MatchUiState,
    selectedCategory: String,
    searchRadius: Float,
    onToggleHelper: (Boolean, String) -> Unit,
    onRequestHelp: (String, Double) -> Unit,
    onDismissPopup: () -> Unit,
    onAcceptMatch: (String) -> Unit,
    onSearchParamUpdate: (String, Float) -> Unit,
    helperDraftCategory: String,
    onHelperDraftChange: (String) -> Unit
) {
    HomeContent(
        currentUser = currentUser,
        isHelperMode = isHelperMode,
        matchState = matchState,
        selectedCategory = selectedCategory,
        searchRadius = searchRadius,
        onToggleHelperMode = onToggleHelper,
        onDismissMatchPopup = onDismissPopup,
        onCategoryChange = { newCat ->
            onSearchParamUpdate(newCat, searchRadius)
        },
        onRadiusChange = { newRad ->
            onSearchParamUpdate(selectedCategory, newRad)
        },
        onSendHelpRequest = {
            // --- CONVERSIONE GEOSPAZIALE ---
            val toleranceInDegrees = searchRadius / 111.32
            val toleranceFixedPoint = toleranceInDegrees * 10_000_000.0

            Log.d("HandyGeo", "Km: $searchRadius -> FixedPoint: $toleranceFixedPoint")

            onRequestHelp(selectedCategory, toleranceFixedPoint)
        },
        onAcceptMatch = onAcceptMatch,
        helperDraftCategory = helperDraftCategory,
        onHelperDraftChange = onHelperDraftChange
    )
}
// Content Stateless (Solo UI)
@Composable
fun HomeContent(
    currentUser: User?,
    isHelperMode: Boolean,
    matchState: MatchUiState,
    selectedCategory: String,
    searchRadius: Float,
    onToggleHelperMode: (Boolean, String) -> Unit,
    onDismissMatchPopup: () -> Unit,
    onCategoryChange: (String) -> Unit,
    onRadiusChange: (Float) -> Unit,
    onSendHelpRequest: () -> Unit,
    onAcceptMatch: (String) -> Unit,
    helperDraftCategory: String,
    onHelperDraftChange: (String) -> Unit
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

            val userIdDisplay = currentUser?.userId?.take(8) ?: "Anonimo"

            // Indicatore ID Utente (Troncato)
            Surface(
                color = HandyPrimary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "ID: $userIdDisplay...",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = HandyPrimary
                )
            }
        }

        Log.d("HandyNav", "isHelperMode: $isHelperMode")
        // Switch Principale (Modalità)
        ModeSwitchCard(
            isHelper = isHelperMode,
            onCheckedChange = { isChecked ->
                if(!isChecked) {
                    Log.d("HandyNav", "Switch OFF")
                    // L'utente accende lo switch:
                    // Passa "Generico" per attivare la UI di configurazione (HelperView)
                    // ma senza far partire ancora l'heartbeat reale
                    onToggleHelperMode(false, "Generico")
                } else {
                    Log.d("HandyNav", "Switch ON")
                    // L'utente spegne lo switch:
                    // Torna richiedente
                    onToggleHelperMode(true, "Generico")
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Log.d("HandyNav", "isHelperMode: $isHelperMode")
        if (isHelperMode) {
            val userCategory = currentUser?.category ?: "Generico"

            HelperView(
                currentCategory = userCategory,
                selectedDraftCategory = helperDraftCategory,
                onDraftChange = onHelperDraftChange,
                onStartService = { selectedCat ->
                    // L'utente ha scelto la categoria e premuto Start -> Aggiorna DB e Server
                    onToggleHelperMode(true, selectedCat)
                }
            )
        } else {
            RequesterView(
                selectedCategory = selectedCategory,
                searchRadius = searchRadius,
                onCategorySelect = onCategoryChange,
                onRadiusChange = onRadiusChange,
                onSearchClick = onSendHelpRequest
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(matchState.statusMessage, fontSize = 12.sp, color = Color.Gray)

        // POPUP MATCH TROVATO
        if (matchState.showMatchPopup) {
            AlertDialog(
                onDismissRequest = onDismissMatchPopup,
                title = {
                    Text(
                        text = "MATCH TROVATO!",
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text("Un Utente sta cercando aiuto vicino a te")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = matchState.statusMessage)
                    }
                },

                confirmButton = {
                    Button(
                        onClick = {
                            matchState.incomingMatchId?.let { onAcceptMatch(it) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HandySecondary)
                    ) {
                        Text("Accetta e Chatta")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissMatchPopup) {
                        Text("Decidi dopo")
                    }
                },
                icon = { Icon(Icons.Filled.Check, contentDescription = null, tint = HandyPrimary) }
            )
        }
    }
}

@Composable
fun HelperView(
    currentCategory: String,
    selectedDraftCategory: String,
    onDraftChange: (String) -> Unit,
    onStartService: (String) -> Unit
) {
    val isServiceActive = currentCategory != "Generico"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        if(!isServiceActive) {
            // --- FASE 1: CONFIGURAZIONE ---
            Text(
                "Qual è la tua competenza?",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = HandyPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Seleziona la tua competenza per renderti visibile.",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryChip("Idraulico", selectedDraftCategory == "Idraulico") { onDraftChange("Idraulico") }
                CategoryChip("Elettricista", selectedDraftCategory == "Elettricista") { onDraftChange("Elettricista") }
                CategoryChip("Medico", selectedDraftCategory == "Medico") { onDraftChange("Medico") }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { onStartService(selectedDraftCategory) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("VAI ONLINE", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            // --- FASE 2: IN SERVIZIO ---
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                "Sei online come $currentCategory",
                fontSize = 20.sp,
                color = HandyPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "In attesa di richieste nelle vicinanze...",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(40.dp))

            // Animazione Radar
            RadarAnimation()

            Spacer(modifier = Modifier.height(150.dp))

            // Bottone per fermare il servizio (torna a Generico/Configurazione)
            OutlinedButton(
                onClick = { onStartService("Generico") },
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Text("Ferma Servizio")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Si consiglia di entrare nell'app frequentemente per una ricerca più veloce")
        }
    }
}

@Composable
fun RequesterView(
    selectedCategory: String,
    searchRadius: Float,
    onCategorySelect: (String) -> Unit,
    onRadiusChange: (Float) -> Unit,
    onSearchClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Di cosa hai bisogno?",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Categorie (Pulsanti semplici per ora, da introdurre meccannismo migliore)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip(
                "Idraulico",
                selectedCategory == "Idraulico"
            ) { onCategorySelect("Idraulico") }
            CategoryChip(
                "Elettricista",
                selectedCategory == "Elettricista"
            ) { onCategorySelect("Elettricista") }
            CategoryChip(
                "Medico",
                selectedCategory == "Medico"
            ) { onCategorySelect("Medico") }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Raggio di ricerca: ${searchRadius.toInt()} km",
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = searchRadius,
            onValueChange = onRadiusChange,
            valueRange = 1f..50f,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSearchClick,
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