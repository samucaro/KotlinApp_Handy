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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.unibo.handy.ui.theme.HandyPrimary

@Composable
fun SignUpScreen(
    state: AuthUiState,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignUpClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF006C75))
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(0.3f),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.handy_icon),
                contentDescription = "Logo Handy App",
                modifier = Modifier
                    .size(180.dp)
                    .padding(top = 16.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(0.7f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Ti diamo il benvenuto",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = HandyPrimary
                )
                Text("Inizia con il  tuo account", color = Color.Gray)

                Spacer(modifier = Modifier.height(24.dp))

                val customTextFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = HandyPrimary,
                    unfocusedTextColor = Color.DarkGray,
                    focusedLabelColor = HandyPrimary,
                    unfocusedLabelColor = Color.DarkGray,
                    focusedBorderColor = HandyPrimary,
                    unfocusedBorderColor = Color.DarkGray
                )

                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsernameChange,
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (state.error != null) {
                    Text(
                        text = state.error,
                        color = Color.Red,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Button(
                    onClick = onSignUpClick,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Registrati", fontSize = 18.sp, color = Color.White)
                    }
                }
            }
        }
    }
}