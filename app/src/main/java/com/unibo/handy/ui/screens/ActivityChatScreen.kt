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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    // Osserviamo la
    val pendingMatches by viewModel.pendingMatches.collectAsState()

    ActivityContent(
        pendingMatches = pendingMatches,
        onAccept = { matchId -> viewModel.acceptMatch(matchId) },
        onReject = { matchId -> viewModel.rejectMatch(matchId) }
    )
}

// Content Stateless
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityContent(
    pendingMatches: List<MatchEntity>,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Le tue Attività", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        if (pendingMatches.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nessuna nuova richiesta.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        "Devi rispondere a queste richieste:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HandyPrimary
                    )
                }
                items(pendingMatches) { match ->
                    PendingMatchItem(
                        match = match,
                        onAccept = { onAccept(match.requesterId) },
                        onReject = { onReject(match.requesterId) }
                    )
                }
            }
        }
    }
}

@Composable
fun PendingMatchItem(match: MatchEntity, onAccept: () -> Unit, onReject: () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color(0xFFE65100), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("?", color = Color.White, fontWeight = FontWeight.Bold) }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Richiesta da ${match.username}", fontWeight = FontWeight.Bold)
                    Text("Categoria: ${match.category}", fontSize = 12.sp, color = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onReject, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rifiuta")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onAccept, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accetta")
                }
            }
        }
    }
}

// ---------------------------------------- CHAT SCREEN ----------------------------------------
// Wrapper Stateful
@Composable
fun ChatScreen(
    viewModel: HomeVM,
    onChatClick: (String) -> Unit
) {
    val activeChats by viewModel.activeChats.collectAsState()
    ChatContent(activeChats = activeChats, onChatClick = onChatClick)
}

// Content Stateless
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContent(activeChats: List<MatchEntity>, onChatClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Messaggi", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        if (activeChats.isEmpty()) {
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
                items(activeChats) { match ->
                    ActiveChatItem(match, onChatClick)
                }
            }
        }
    }
}

@Composable
fun ActiveChatItem(match: MatchEntity, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(match.requesterId) }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = HandyPrimary,
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

        // Orario
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        Text(
            text = timeFormat.format(match.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f), thickness = 1.dp)
}
