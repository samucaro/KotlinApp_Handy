package com.unibo.handy.ui.features.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unibo.handy.domain.model.User
import com.unibo.handy.ui.components.LetterAvatar
import com.unibo.handy.ui.theme.HandyPrimary

@Composable
fun ProfileScreen(
    currentUser: User?,
    onLogout: () -> Unit
) {
    val username = currentUser?.username ?: "Anonimo"
    ProfileContent(
        username = username,
        onLogout = onLogout
    )
}

@Composable
fun ProfileContent(
    username: String,
    onLogout: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = "Attenzione", tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text(text = "Sei sicuro di voler uscire?")
            },
            text = {
                Text(
                    text = "Il logout comporterà la distruzione irreversibile della tua identità locale, inclusa tutta la cronologia delle chat e i match salvati in questo dispositivo. Vuoi procedere?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sì, distruggi i dati ed esci")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text("Annulla")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HandyPrimary)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.3f),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LetterAvatar(username)
                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    username,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Profilo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text("Gestione Account", modifier = Modifier.padding(8.dp))
                HorizontalDivider(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    thickness = 1.dp
                )

                Text("Privacy & Sicurezza", modifier = Modifier.padding(8.dp))
                HorizontalDivider(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    thickness = 1.dp
                )

                Spacer(modifier = Modifier.weight(1f))

                OutlinedButton(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Logout",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Logout")
                }
            }
        }
    }
}