package com.unibo.handy.ui.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.unibo.handy.ui.features.auth.AuthState.LOGGED_IN
import com.unibo.handy.ui.features.auth.AuthState.NOT_LOGGED
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.unibo.handy.data.network.NetworkStatus
import com.unibo.handy.ui.components.OfflineBlockScreen
import com.unibo.handy.ui.features.auth.SignUpScreen
import com.unibo.handy.ui.features.chat.SingleChatScreen
import com.unibo.handy.ui.features.splash.SplashScreen
import com.unibo.handy.ui.features.auth.AuthViewModel
import com.unibo.handy.ui.features.chat.ChatViewModel
import com.unibo.handy.ui.features.match.MatchViewModel
import com.unibo.handy.ui.features.user.UserViewModel

sealed class Screen(val route: String) {
    object Splash : Screen("splash_screen")
    object SignUp : Screen("signup_screen")
    object Home : Screen("home_screen")
    object ChatDetail : Screen("chat_detail/{matchId}") {
        fun createRoute(matchId: String) = "chat_detail/$matchId"
    }
}

/**
 * Entry Point Navigazionale dell'App.
 * Definisce il grafo di navigazione e gestisce gli overlay globali (es. Server Offline).
 */
@SuppressLint("ServiceCast")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandyAppEntry() {
    val navController = rememberNavController()
    val userVM: UserViewModel = hiltViewModel()
    val netStatus by userVM.networkStatus.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route
        ) {
            // --- ROTTA 1: SPLASH SCREEN ---
            composable(Screen.Splash.route) {
                val authVM: AuthViewModel = hiltViewModel()
                val authState by authVM.authState.collectAsState()

                LaunchedEffect(authState) {
                    if (authState == LOGGED_IN) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    } else if (authState == NOT_LOGGED) {
                        navController.navigate(Screen.SignUp.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                }
                SplashScreen()
            }

            // --- ROTTA 2: SIGN UP ---
            composable(Screen.SignUp.route) {
                val authVM: AuthViewModel = hiltViewModel()
                val state by authVM.uiState.collectAsState()

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

            // --- ROTTA 3: DASHBOARD PRINCIPALE ---
            composable(Screen.Home.route) {
                val userVM: UserViewModel = hiltViewModel()
                val userState by userVM.uiState.collectAsState()

                val matchVM: MatchViewModel = hiltViewModel()
                val matchState by matchVM.uiState.collectAsState()
                val pendingMatches by matchVM.pendingMatches.collectAsState()
                val activeChatsAsHelper by matchVM.activeChatsAsHelper.collectAsState()
                val activeChatsAsRequester by matchVM.activeChatsAsRequester.collectAsState()

                val context = LocalContext.current

                // Feedback (Vibrazione) retrocompatibile
                LaunchedEffect(matchState.incomingMatchId) {
                    if (matchState.showMatchPopup && matchState.incomingMatchId != null) {
                        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                        val vibrator = vibratorManager.defaultVibrator

                        if (vibrator.hasVibrator()) {
                            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                }

                MainScreen(
                    userState = userState,
                    matchState = matchState,
                    pendingMatches = pendingMatches,
                    activeChatsAsRequester = activeChatsAsRequester,
                    activeChatsAsHelper = activeChatsAsHelper,
                    onToggleHelper = userVM::toggleHelperMode,
                    onRequestHelp = { cat, rad -> userVM.sendHelpRequest(cat, rad) },
                    onOpenChat = { matchId ->
                        navController.navigate(Screen.ChatDetail.createRoute(matchId))
                    },
                    onDismissPopup = matchVM::dismissPopup,
                    onAcceptMatch = { matchId ->
                        val requesterId = matchState.incomingRequesterId ?: ""
                        matchVM.acceptMatch(matchId, requesterId)
                        navController.navigate(Screen.ChatDetail.createRoute(matchId))
                    },
                    onRejectMatch = matchVM::rejectMatch,
                    onSearchParamUpdate = userVM::updateSearchParameters,
                    onHelperDraftChange = userVM::updateHelperDraft,
                    onLogout = userVM::logout
                )
            }

            // --- ROTTA 4: DETTAGLIO CHAT ---
            composable(Screen.ChatDetail.route) { backStackEntry ->
                val matchId = backStackEntry.arguments?.getString("matchId") ?: return@composable
                val chatVM: ChatViewModel = hiltViewModel()
                val userVM: UserViewModel = hiltViewModel()
                val userState by userVM.uiState.collectAsState()

                LaunchedEffect(matchId) {
                    chatVM.loadMessages(matchId)
                }

                SingleChatScreen(
                    viewModel = chatVM,
                    matchId = matchId,
                    myUserId = userState.currentUser?.userId ?: "",
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // --- GLOBAL OVERLAY: GESTIONE OFFLINE ---
        if (netStatus is NetworkStatus.Disconnected || netStatus is NetworkStatus.Reconnecting) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent
            ) {
                OfflineBlockScreen(
                    isReconnecting = netStatus is NetworkStatus.Reconnecting,
                    onRetry = { userVM.retryConnection() }
                )
            }
        }
    }
}