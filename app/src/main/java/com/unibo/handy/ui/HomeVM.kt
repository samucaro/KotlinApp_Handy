package com.unibo.handy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unibo.handy.HandyApp
import com.unibo.handy.data.repository.ChatRepository
import com.unibo.handy.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeVM(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState()) // privata (visibile solo dal VM)
    // uiState è lo specchio di _uiState quindi chiunque cambia compose percepisce la modifica
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow() // pubblica (visibile alla UI Compose)

    init {
        // Il VM non "fa" le cose, "osserva" i cambiamenti fatti da altri (Repo/Service)
        observeUserUpdates()
        observeMatches()
        observeMatchEvents()
    }

    // --- OSSERVAZIONE REATTIVA (Observer Pattern) ---
    private fun observeUserUpdates() {
        viewModelScope.launch {
            userRepository.currentUserFlow.collect { user ->
                if (user == null) {
                    //it rappresenta lo stato attuale, copy() crea una copia di HomeUiState che
                    // permette a Compose di percepire un cambiamento di stato (nuova istanza)
                    _uiState.update { it.copy(userId = "") }
                } else {
                    _uiState.update {
                        it.copy(
                            userId = user.userId,
                            username = user.username,
                            isHelperMode = user.helpModeActive,
                            selectedCategory = user.category
                        )
                    }
                }
            }
        }
    }

    private fun observeMatches() {
        viewModelScope.launch {
            // Appena il Service o la strategia trovano un match e lo salvano nel DB,
            // questo flusso scatta e aggiorna la lista a video
            userRepository.matchesFlow.collect { list ->
                _uiState.update { it.copy(matchesList = list) }
            }
        }
    }

    private fun observeMatchEvents() {
        viewModelScope.launch {
            userRepository.matchEvents.collect { matchMessage ->
                _uiState.update {
                    it.copy(
                        statusMessage = "MATCH TROVATO! $matchMessage",
                        showMatchSuccess = true
                    )
                }
            }
        }
    }

    //---------------------------------------- AZIONI UTENTE ---------------------------------------

    // AZIONE 1: Cambia Modalità (Helper <-> Requester con il fleg)
    fun toggleHelperMode(isActive: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isHelperMode = isActive)
            }
            userRepository.setHelperMode(isActive)
        }
    }

    // AZIONE 2: Cambia Parametri Ricerca
    fun updateSearchParameters(category: String, radius: Float) {
        _uiState.update { it.copy(selectedCategory = category, toleranceRadius = radius) }
    }

    // AZIONE 3: Invia Richiesta di Aiuto
    fun sendHelpRequest() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(statusMessage = "Invio richiesta in corso...") }

            try {
                userRepository.sendHelpRequest(
                    category = state.selectedCategory,
                    tolerance = state.toleranceRadius.toDouble()
                )
                _uiState.update { it.copy(statusMessage = "Richiesta inviata! In attesa di match...") }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Errore invio: ${e.message}") }
            }
        }
    }

    // REGISTRAZIONE/AGGIORNAMENTO profilo
    fun updateUserProfile(username: String, email: String, psw: String, category: String) {
        viewModelScope.launch {
            try {
                // Salvataggio utente creato nel db locale
                userRepository.updateUserProfile(username, email, psw, category)
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Errore durante la registrazione: ${e.message}") }
            }
        }
    }

    fun dismissMatchPopup() {
        _uiState.update { it.copy(showMatchSuccess = false, statusMessage = "Pronto") }
    }

    // --- 3. SEZIONE CHAT (Future Improvement: Spostare in ChatViewModel) ---
    // Per ora va bene qui per semplicità, ma viola leggermente SRP.
    fun getChatMessages(chatId: String) = chatRepository.getMessagesFlow(chatId)

    fun sendMessage(recipientId: String, text: String) {
        viewModelScope.launch {
            chatRepository.sendMessage(recipientId, text)
        }
    }

    // ------------------------------------------ FACTORY ------------------------------------------
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    (this[ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY] as HandyApp)
                HomeVM(
                    userRepository = application.userRepository,
                    chatRepository = application.chatRepository
                )

            }
        }
    }
}