package com.unibo.handy.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.unibo.handy.data.db.entity.MatchEntity
import com.unibo.handy.ui.components.NavBarItem
import com.unibo.handy.ui.features.chat.ActivityScreen
import com.unibo.handy.ui.features.chat.ChatListScreen
import com.unibo.handy.ui.features.match.MatchUiState
import com.unibo.handy.ui.features.user.ProfileScreen
import com.unibo.handy.ui.features.user.UserUiState
import com.unibo.handy.ui.features.home.HomeScreen
import com.unibo.handy.ui.theme.HandyPrimary

/**
 * Wrapper architetturale per la navigazione interna (Bottom Navigation).
 * Instrada lo stato dei ViewModel alle singole schermate (Hoisting dello stato).
 */
@Composable
fun MainScreen(
    userState: UserUiState,
    matchState: MatchUiState,
    pendingMatches: List<MatchEntity>,
    activeChatsAsRequester: List<MatchEntity>,
    activeChatsAsHelper: List<MatchEntity>,
    onToggleHelper: (Boolean, String) -> Unit,
    onRequestHelp: (String, Double) -> Unit,
    onOpenChat: (String) -> Unit,
    onDismissPopup: () -> Unit,
    onAcceptMatch: (String) -> Unit,
    onRejectMatch: (String) -> Unit,
    onSearchParamUpdate: (String, Float) -> Unit,
    onHelperDraftChange: (String) -> Unit,
    onLogout: () -> Unit
) {
    // Stato locale per la navigazione della BottomBar
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavBarItem(0, "Home", Icons.Default.Home, selectedTab) { selectedTab = 0 }
                NavBarItem(1, "Attività", Icons.AutoMirrored.Filled.List, selectedTab) { selectedTab = 1 }
                NavBarItem(2, "Chat", Icons.Default.Sms, selectedTab) { selectedTab = 2 }
                NavBarItem(3, "Profilo", Icons.Default.Person, selectedTab) { selectedTab = 3 }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(Color(0xFFF5F7F8))
        ) {
            // Stato di caricamento (Wait for local DB)
            if (!userState.isInitialDataLoaded) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = HandyPrimary)
                }
            } else {
                // Sostituzione dinamica del contenuto basata sul tab selezionato
                when (selectedTab) {
                    0 -> HomeScreen(
                        currentUser = userState.currentUser,
                        isHelperMode = userState.isHelperMode,
                        userStatusMessage = userState.statusMessage,
                        matchState = matchState,
                        selectedCategory = userState.selectedCategory,
                        searchRadius = userState.searchRadius,
                        onToggleHelper = onToggleHelper,
                        onRequestHelp = onRequestHelp,
                        onDismissPopup = onDismissPopup,
                        onAcceptMatch = onAcceptMatch,
                        onSearchParamUpdate = onSearchParamUpdate,
                        helperDraftCategory = userState.helperCategoryDraft,
                        onHelperDraftChange = onHelperDraftChange
                    )
                    1 -> ActivityScreen(
                        pendingMatches = pendingMatches,
                        onAccept = onAcceptMatch,
                        onReject = onRejectMatch
                    )
                    2 -> ChatListScreen(
                        activeChatsAsRequester = activeChatsAsRequester,
                        activeChatsAsHelper = activeChatsAsHelper,
                        onChatClick = onOpenChat
                    )
                    else -> ProfileScreen(
                        currentUser = userState.currentUser,
                        onLogout = onLogout
                        )
                }
            }
        }
    }
}