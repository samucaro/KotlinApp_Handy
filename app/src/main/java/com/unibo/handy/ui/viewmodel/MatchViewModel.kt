package com.unibo.handy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unibo.handy.data.db.dao.MatchDAO
import com.unibo.handy.data.repository.ChatRepository
import com.unibo.handy.data.repository.MatchingRepository
import com.unibo.handy.ui.MatchUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MatchViewModel @Inject constructor(
    private val matchingRepository: MatchingRepository,
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
        // Osserva eventi di Match (WebSocket -> Repo -> VM)
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
}