package com.unibo.handy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unibo.handy.data.db.entity.MatchEntity
import com.unibo.handy.ui.components.EmptyStateMessage
import com.unibo.handy.ui.HomeVM
import com.unibo.handy.ui.theme.HandyPrimary
import java.text.SimpleDateFormat
import java.util.Locale

// Wrapper Stateful
@Composable
fun ActivityScreen(viewModel: HomeVM, onChatClick: (String) -> Unit) {
    // Osserviamo la lista dei match dal ViewModel
    val state by viewModel.uiState.collectAsState()

    ActivityContent(
        matchesList = state.matchesList,
        onChatClick = onChatClick
    )
}

// Content Stateless
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityContent(
    matchesList: List<MatchEntity>,
    onChatClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // TopBar integrata nella pagina
        TopAppBar(
            title = { Text("Le tue Attività", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        if (matchesList.isEmpty()) {
            EmptyStateMessage()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Match Recenti",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(matchesList) { match ->
                    ActivityMatchItem(match, onChatClick)
                }
            }
        }
    }
}

// --- Componente: La singola riga (Card) del Match ---
@Composable
fun ActivityMatchItem(match: MatchEntity, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(match.requesterId) },
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar con Iniziale
            Surface(
                shape = CircleShape,
                color = HandyPrimary,
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = match.username.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Dati Testuali
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = match.username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Categoria: ${match.category}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray
                )

                // Formattazione Data
                val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                Text(
                    text = dateFormat.format(match.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            // Icona freccia
            Icon(
                imageVector = Icons.Default.ChevronRight, // O ArrowForward
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }
}

// ---------------------------------------- ACTIVITY SCREEN ----------------------------------------
// Wrapper Stateful
@Composable
fun ChatScreen(
    viewModel: HomeVM,
    onChatClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    ChatContent(matchesList = state.matchesList, onChatClick = onChatClick)
}

// Content Stateless
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContent(matchesList: List<MatchEntity>, onChatClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Messaggi", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        if (matchesList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Nessuna conversazione attiva", color = Color.Gray)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(matchesList) { match ->
                    ChatItem(match, onChatClick)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ChatItem(match: MatchEntity, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(match.requesterId) } // Apre la chat singola
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar Rotondo
        Surface(
            shape = CircleShape,
            color = Color(0xFF006C75), // HandyPrimary
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = match.username.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Nome e Ultimo Messaggio (Simulato per ora)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                match.username,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tocca per chattare...", // Qui in futuro metteremo l'ultimo messaggio vero
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                maxLines = 1
            )
        }

        // Orario (Simulato dalla data del match)
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(match.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f), thickness = 1.dp)
}
