package com.unibo.handy.ui.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.unibo.handy.ui.screens.MainScreen
import com.unibo.handy.ui.screens.SignUpScreen
import com.unibo.handy.ui.screens.SingleChatScreen
import com.unibo.handy.ui.viewmodel.AuthViewModel
import com.unibo.handy.ui.viewmodel.ChatViewModel
import com.unibo.handy.ui.viewmodel.MatchViewModel

// Indirizzi delle rotte (schermate) con Jetpack Navigation
sealed class Screen(val route: String) {
    object SignUp : Screen("signup_screen")
    object Home : Screen("home_screen")
    object ChatDetail : Screen("chat_detail/{matchId}") {
        fun createRoute(matchId: String) = "chat_detail/$matchId"
    }
}

// --- ENTRY POINT ---
@SuppressLint("ServiceCast")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandyAppEntry() {
    /*val viewModel: HomeVM = viewModel(factory = HomeVM.Factory)
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
    }*/
    val navController = rememberNavController()

    // Contenitore che cambia schermata
    NavHost(
        navController = navController,
        startDestination = Screen.SignUp.route
    ) {
        // ROTTA 1: Schermata di Signup
        composable(Screen.SignUp.route) {
            // Inietto AuthViewModel
            val authVM: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
            val state by authVM.uiState.collectAsState()

            // Controllo navigazione
            LaunchedEffect(state.isSignUpSuccess) {
                if (state.isSignUpSuccess) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.SignUp.route) { inclusive = true }
                    }
                }
            }

            SignUpScreen(
                state = state,
                onUsernameChange = authVM::onUsernameChange,
                onEmailChange = authVM::onEmailChange,
                onPasswordChange = authVM::onPasswordChange,
                onSignUpClick = authVM::signUp
            )
            /*val state by authVM.uiState.collectAsState()
            SignUpScreen(
                viewModel = viewModel,
                // Quando la registrazione manuale finisce (click bottone), passa a Home
                onSignUpSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.SignUp.route) { inclusive = true }
                    }
                }
            )*/
        }

        // ROTTA 2: DASHBOARD
        composable(Screen.Home.route) {
            // Inietto MapViewModel
            val matchVM: MatchViewModel = viewModel(factory = MatchViewModel.Factory)
            val state by matchVM.uiState.collectAsState()
            val pendingMatches by matchVM.pendingMatches.collectAsState()
            val activeChats by matchVM.activeChats.collectAsState()

            val context = LocalContext.current // Serve per accedere al sistema di vibrazione

            // Gestisce l'apertura della chat se clicco sul popup del match
            LaunchedEffect(state.incomingMatchId) {
                if(state.showMatchPopup && state.incomingMatchId != null) {
                    // Vibrazione
                    val vibratorManager =
                        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    val vibrator = vibratorManager.defaultVibrator
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            500,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                    Log.d("HandyNav", "Popup mostrato per match: ${state.incomingMatchId}")
                }
            }

            MainScreen(
                state = state,
                pendingMatches = pendingMatches,
                activeChats = activeChats,
                onToggleHelper = matchVM::toggleHelperMode,
                onRequestHelp = { cat, rad -> matchVM.sendHelpRequest(cat, rad) },
                onOpenChat = { matchId ->
                    navController.navigate(Screen.ChatDetail.createRoute(matchId))
                },
                onDismissPopup = matchVM::dismissPopup,
                onAcceptMatch = { matchId ->
                    // 1. Avvisa il ViewModel (aggiorna DB, manda notifica al server)
                    matchVM.acceptMatch(matchId)

                    // 2. NAVIGA subito alla chat
                    navController.navigate(Screen.ChatDetail.createRoute(matchId))
                },
                onRejectMatch = matchVM::rejectMatch,
                onSearchParamUpdate = matchVM::updateSearchParameters
            )
            /*MainScreen(
                viewModel = viewModel,
                onOpenChat = { matchId ->
                    // Quando clicco su una chat, navigo al dettaglio
                    navController.navigate(Screen.ChatDetail.createRoute(matchId))
                }
            )*/
        }

        // ROTTA 3: DETTAGLIO CHAT
        composable(Screen.ChatDetail.route) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getString("matchId") ?: return@composable
            // Inietto ChatViewModel
            val chatVM: ChatViewModel = viewModel(factory = ChatViewModel.Factory)

            // Carica i messaggi per questo ID specifico
            LaunchedEffect(matchId) {
                chatVM.loadMessages(matchId)
            }

            SingleChatScreen(
                viewModel = chatVM,
                matchId = matchId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}