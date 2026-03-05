package com.unibo.handy.ui.features.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unibo.handy.data.db.dao.MatchDAO
import com.unibo.handy.data.repository.ChatRepository
import com.unibo.handy.data.repository.MatchingRepository
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

    // FLUSSO SDOPPIATO PER LE CHAT
    val activeChatsAsHelper = matchDao.getActiveChatsAsHelper()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeChatsAsRequester = matchDao.getActiveChatsAsRequester()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Osserva eventi di Match (WebSocket -> Repo -> VM)
        viewModelScope.launch {
            matchingRepository.matchEvents.collectLatest { matchData ->
                _uiState.update {
                    it.copy(
                        showMatchPopup = true,
                        incomingMatchId = matchData.first,
                        incomingRequesterId = matchData.second,
                        statusMessage = "Un Utente sta cercando aiuto vicino a te"
                    )
                }
            }
        }
    }

    // --- AZIONI ---
    fun acceptMatch(matchId: String, requesterId: String) {
        viewModelScope.launch {
            chatRepository.acceptMatch(matchId, requesterId)
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