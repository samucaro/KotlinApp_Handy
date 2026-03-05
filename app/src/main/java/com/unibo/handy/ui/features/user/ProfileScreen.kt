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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unibo.handy.domain.model.User
import com.unibo.handy.ui.components.LetterAvatar
import com.unibo.handy.ui.theme.HandyPrimary

// 1. Wrapper Stateful
@Composable
fun ProfileScreen(currentUser: User?) {
    val username = currentUser?.username ?: "Anonimo"
    ProfileContent(username = username)
}

// 2. Content Stateless
@Composable
fun ProfileContent(username: String) {
    Column(
        modifier = Modifier.Companion
            .fillMaxSize()
            .background(HandyPrimary)
    ) {
        // Parte superiore: Immagine e Nome
        Box(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .weight(0.3f),
            contentAlignment = Alignment.Companion.Center
        ) {
            Row(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.Companion.CenterVertically
            ) {
                LetterAvatar(username)
                Spacer(modifier = Modifier.Companion.width(16.dp))

                Text(
                    username,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.Companion.White,
                    fontWeight = FontWeight.Companion.Bold,
                    textAlign = TextAlign.Companion.Center
                )
            }
        }

        // Parte inferiore: Card bianca con i campi
        Surface(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .weight(0.7f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = Color.Companion.White
        ) {
            Column(
                modifier = Modifier.Companion
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Companion.Start
            ) {
                Spacer(modifier = Modifier.Companion.height(24.dp))
                Text(
                    "Profilo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Companion.Bold
                )

                Spacer(modifier = Modifier.Companion.height(24.dp))

                Text("Gestione Account", modifier = Modifier.Companion.padding(8.dp))
                HorizontalDivider(
                    color = Color.Companion.LightGray.copy(alpha = 0.3f),
                    thickness = 1.dp
                )
                Text("Privacy & Sicurezza", modifier = Modifier.Companion.padding(8.dp))
                HorizontalDivider(
                    color = Color.Companion.LightGray.copy(alpha = 0.3f),
                    thickness = 1.dp
                )
            }
        }
    }
}