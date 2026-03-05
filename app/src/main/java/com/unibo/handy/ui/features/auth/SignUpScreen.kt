package com.unibo.handy.ui.features.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unibo.handy.R

@Composable
fun SignUpScreen(
    state: AuthUiState,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignUpClick: () -> Unit
) {
    Column(
        modifier = Modifier.Companion
            .fillMaxSize()
            .background(Color(0xFF006C75))
    ) {
        // Parte superiore: Logo e Immagine (30% dello schermo)
        Box(
            modifier = Modifier.Companion.fillMaxWidth().weight(0.3f),
            contentAlignment = Alignment.Companion.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.handy_icon),
                contentDescription = "Logo Handy App",
                modifier = Modifier.Companion
                    .size(180.dp)
                    .padding(top = 16.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Companion.Crop
            )
        }

        // Parte inferiore: Card bianca con i campi (70% dello schermo)
        Surface(
            modifier = Modifier.Companion.fillMaxWidth().weight(0.7f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = Color.Companion.White
        ) {
            Column(
                modifier = Modifier.Companion.padding(24.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Companion.CenterHorizontally
            ) {
                Text(
                    "Ti diamo il benvenuto",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Companion.Bold
                )
                Text("Inizia con il  tuo account", color = Color.Companion.Gray)

                Spacer(modifier = Modifier.Companion.height(24.dp))

                // CAMPI DI INPUT
                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsernameChange,
                    label = { Text("Username") },
                    modifier = Modifier.Companion.fillMaxWidth()
                )

                Spacer(modifier = Modifier.Companion.height(12.dp))

                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    modifier = Modifier.Companion.fillMaxWidth()
                )

                Spacer(modifier = Modifier.Companion.height(12.dp))

                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.Companion.fillMaxWidth()
                )

                Spacer(modifier = Modifier.Companion.height(32.dp))

                if (state.error != null) {
                    Text(
                        text = state.error,
                        color = Color.Companion.Red,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Companion.SemiBold,
                        modifier = Modifier.Companion.padding(bottom = 8.dp)
                    )
                }

                // BOTTONE DI REGISTRAZIONE
                Button(
                    onClick = onSignUpClick,
                    enabled = !state.isLoading,
                    modifier = Modifier.Companion.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
                ) {
                    if (state.isLoading) {
                        // Mostra la rotellina di caricamento
                        CircularProgressIndicator(
                            color = Color.Companion.White,
                            modifier = Modifier.Companion.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Registrati", fontSize = 18.sp, color = Color.Companion.White)
                    }
                }
            }
        }
    }
}