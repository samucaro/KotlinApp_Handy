package com.unibo.handy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unibo.handy.HandyApp
import com.unibo.handy.data.db.dao.MatchDAO
import com.unibo.handy.data.repository.ChatRepository
import com.unibo.handy.data.repository.MatchingRepository
import com.unibo.handy.data.repository.UserRepository
import com.unibo.handy.ui.MatchUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MatchViewModel(
    private val matchingRepository: MatchingRepository,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    matchDao: MatchDAO
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState = _uiState.asStateFlow()

    // Flow dei match in attesa per la lista nella Home
    val pendingMatches = matchDao.getPendingMatches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Flow per la lista Chat (usato da MainScreen -> ChatListScreen)
    val activeChats = matchDao.getActiveChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // 1. Osserva lo stato Helper/User
        viewModelScope.launch {
            userRepository.currentUserFlow.collectLatest { user ->
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        isHelperMode = user?.helpModeActive ?: false
                    )
                }
            }
        }

        // 2. Osserva eventi di Match (WebSocket -> Repo -> VM)
        viewModelScope.launch {
            matchingRepository.matchEvents.collectLatest { requesterId ->
                _uiState.update {
                    it.copy(
                        showMatchPopup = true,
                        incomingMatchId = requesterId,
                        statusMessage = "Un Utente sta cercando aiuto vicino a te"
                    )
                }
            }
        }
    }

    // --- AZIONI ---
    fun toggleHelperMode(isActive: Boolean) {
        viewModelScope.launch { userRepository.setHelperMode(isActive) }
    }

    fun updateSearchParameters(category: String, radius: Float) {
        _uiState.update {
            it.copy(selectedCategory = category, toleranceRadius = radius)
        }
    }

    fun sendHelpRequest(category: String, radius: Double) {
        viewModelScope.launch {
            val state = _uiState.value
            val userId = state.currentUser?.userId ?: return@launch

            _uiState.update { it.copy(statusMessage = "Invio richiesta in corso...") }

            try {
                matchingRepository.sendHelpRequest(
                    userId = userId,
                    category = category,
                    tolerance = radius
                )
                _uiState.update { it.copy(statusMessage = "Richiesta Inviata! Attendi...") }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Errore: ${e.message}") }
            }
        }
    }

    fun acceptMatch(matchId: String) {
        viewModelScope.launch {
            chatRepository.acceptMatch(matchId)
            dismissPopup()
        }
    }

    fun rejectMatch(matchId: String) {
        viewModelScope.launch {
            chatRepository.rejectMatch(matchId)
            dismissPopup()
        }
    }

    fun dismissPopup() {
        _uiState.update { it.copy(showMatchPopup = false) }
    }

    // Factory
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HandyApp)
                MatchViewModel(
                    app.matchingRepository,
                    app.userRepository,
                    app.chatRepository,
                    app.db.matchDao()
                )
            }
        }
    }
}