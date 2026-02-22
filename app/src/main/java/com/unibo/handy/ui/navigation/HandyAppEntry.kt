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
import com.unibo.handy.ui.viewmodel.AuthState.LOGGED_IN
import com.unibo.handy.ui.viewmodel.AuthState.NOT_LOGGED
import com.unibo.handy.ui.viewmodel.AuthState.LOADING
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.unibo.handy.data.network.NetworkStatus
import com.unibo.handy.ui.screens.MainScreen
import com.unibo.handy.ui.screens.OfflineBlockScreen
import com.unibo.handy.ui.screens.SignUpScreen
import com.unibo.handy.ui.screens.SingleChatScreen
import com.unibo.handy.ui.screens.SplashScreen
import com.unibo.handy.ui.viewmodel.AuthViewModel
import com.unibo.handy.ui.viewmodel.ChatViewModel
import com.unibo.handy.ui.viewmodel.MatchViewModel
import com.unibo.handy.ui.viewmodel.UserViewModel

// Indirizzi delle rotte (schermate) con Jetpack Navigation
sealed class Screen(val route: String) {
    object Splash : Screen("splash_screen")
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
    val navController = rememberNavController()

    val userVM: UserViewModel = viewModel(factory = UserViewModel.Factory)
    val netStatus by userVM.networkStatus.collectAsState()

    // --- LOGICA DI BLOCCO ---
    when (netStatus) {
        is NetworkStatus.Disconnected -> {
            OfflineBlockScreen(
                onRetry = { userVM.retryConnection() }
            )
        }
        is NetworkStatus.Initializing,
        is NetworkStatus.Connected -> {
            // Contenitore che cambia schermata
            NavHost(
                navController = navController,
                startDestination = Screen.Splash.route
            ) {
                // --- ROTTA 1: SPLASH SCREEN ---
                composable(Screen.Splash.route) {
                    val authVM: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
                    val authState by authVM.authState.collectAsState()

                    //Naviga appena lo stato cambia
                    LaunchedEffect(authState, netStatus) {
                        when (authState) {
                            LOGGED_IN -> {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            }

                            NOT_LOGGED -> {
                                // Nessun utente -> VAI AL SIGNUP
                                navController.navigate(Screen.SignUp.route) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            }

                            LOADING -> {}
                        }
                    }
                    SplashScreen()
                }

                // ROTTA 2: Schermata di Signup
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
                }

                // ROTTA 3: DASHBOARD
                composable(Screen.Home.route) {
                    // Inietto ViewModel
                    val userVM: UserViewModel = viewModel(factory = UserViewModel.Factory)
                    val userState by userVM.uiState.collectAsState()
                    val matchVM: MatchViewModel = viewModel(factory = MatchViewModel.Factory)
                    val matchState by matchVM.uiState.collectAsState()
                    val pendingMatches by matchVM.pendingMatches.collectAsState()
                    val activeChats by matchVM.activeChats.collectAsState()

                    val context =
                        LocalContext.current // Serve per accedere al sistema di vibrazione

                    // Gestisce l'apertura della chat se clicco sul popup del match
                    LaunchedEffect(matchState.incomingMatchId) {
                        if (matchState.showMatchPopup && matchState.incomingMatchId != null) {
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
                            Log.d(
                                "HandyNav",
                                "Popup mostrato per match: ${matchState.incomingMatchId}"
                            )
                        }
                    }

                    MainScreen(
                        userState = userState,
                        matchState = matchState,
                        pendingMatches = pendingMatches,
                        activeChats = activeChats,
                        onToggleHelper = userVM::toggleHelperMode,
                        onRequestHelp = { cat, rad -> userVM.sendHelpRequest(cat, rad) },
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
                        onSearchParamUpdate = userVM::updateSearchParameters,
                        onHelperDraftChange = userVM::updateHelperDraft
                    )
                }

                // ROTTA 4: DETTAGLIO CHAT
                composable(Screen.ChatDetail.route) { backStackEntry ->
                    val matchId =
                        backStackEntry.arguments?.getString("matchId") ?: return@composable
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
    }
}