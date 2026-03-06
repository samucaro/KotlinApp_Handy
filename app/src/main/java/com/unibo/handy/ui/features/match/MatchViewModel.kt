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

/**
 * ViewModel delegato alla gestione del ruolo "Helper".
 * Orchestra l'arrivo di nuove richieste di aiuto, l'accettazione/rifiuto dei match
 * e l'alimentazione delle liste delle chat attive.
 */
@HiltViewModel
class MatchViewModel @Inject constructor(
    private val matchingRepository: MatchingRepository,
    private val chatRepository: ChatRepository,
    matchDao: MatchDAO
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState = _uiState.asStateFlow()

    val pendingMatches = matchDao.getPendingMatches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeChatsAsHelper = matchDao.getActiveChatsAsHelper()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeChatsAsRequester = matchDao.getActiveChatsAsRequester()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // --- EVENT LISTENER (Reattività di Rete) ---
        // Rimane in ascolto di eventi emessi dal Repository quando il PrivacyEngine
        // risolve con successo una Tupla (Distanza < Tolleranza)
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

    // --- AZIONI INTENZIONALI (User Intents) ---
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