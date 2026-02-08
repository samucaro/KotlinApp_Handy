package com.unibo.handy.ui.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.unibo.handy.ui.HomeVM
import com.unibo.handy.ui.screens.MainScreen
import com.unibo.handy.ui.screens.SignUpScreen
import com.unibo.handy.ui.screens.SingleChatScreen

// Indirizzi delle rotte (schermate) con Jetpack Navigation
sealed class Screen(val route: String) {
    object SignUp : Screen("signup_screen")
    object Home : Screen("home_screen")
    object ChatDetail : Screen("chat_detail/{matchId}") {
        fun createRoute(matchId: String) = "chat_detail/$matchId"
    }
}

// --- ENTRY POINT ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandyAppEntry() {
    val viewModel: HomeVM = viewModel(factory = HomeVM.Factory)
    val state by viewModel.uiState.collectAsState()
    val navController = rememberNavController()

    LaunchedEffect(state.userId) {
        // Se l'ID non è vuoto
        if (state.userId.isNotBlank()) {
            if (navController.currentDestination?.route == Screen.SignUp.route) {
                navController.navigate(Screen.Home.route) {
                    // "popUpTo" serve a cancellare la cronologia dato che deve tornare al Signup
                    popUpTo(Screen.SignUp.route) { inclusive = true }
                }
            }
        }
    }

    // Contenitore che cambia schermata
    NavHost(
        navController = navController,
        startDestination = Screen.SignUp.route
    ) {
        // ROTTA 1: Schermata di Signup
        composable(Screen.SignUp.route) {
            SignUpScreen(
                viewModel = viewModel,
                // Quando la registrazione manuale finisce (click bottone), passa a Home
                onSignUpSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.SignUp.route) { inclusive = true }
                    }
                }
            )
        }
        // ROTTA 2: DASHBOARD
        composable(Screen.Home.route) {
            MainScreen(
                viewModel = viewModel,
                onOpenChat = { matchId ->
                    // Quando clicco su una chat, navigo al dettaglio
                    navController.navigate(Screen.ChatDetail.createRoute(matchId))
                }
            )
        }
        // ROTTA 3: DETTAGLIO CHAT
        composable(Screen.ChatDetail.route) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getString("matchId") ?: return@composable
            SingleChatScreen(
                viewModel = viewModel,
                matchId = matchId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}