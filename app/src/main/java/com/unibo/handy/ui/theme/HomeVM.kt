package com.unibo.handy.ui.theme

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unibo.handy.HandyApp
import com.unibo.handy.data.repository.ChatRepository
import com.unibo.handy.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeVM(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState()) // privata (visibile solo dal VM)
    // uiState è lo specchio di _uiState quindi chiunque cambia compose percepisce la modifica
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow() // pubblica (visibile alla UI Compose)


    private var heartbeatJob: Job? = null

    init {
        loadUser()
        listenForMatches()

        viewModelScope.launch {
            userRepository.matchesFlow.collect { list ->
                _uiState.update { it.copy(matchesList = list) }
            }
        }
    }

    private fun loadUser() {
        viewModelScope.launch {
            userRepository.currentUserFlow.collect { user ->
                if (user == null) {
                    //it rappresenta lo stato attuale, copy() crea una copia di HomeUiState che
                    // permette a Compose di percepire un cambiamento di stato (nuova istanza)
                    _uiState.update { it.copy(userId = "") }
                    //simulateRegistration()
                } else {
                    _uiState.update {
                        it.copy(
                            userId = user.userId,
                            username = user.username,
                            isHelperMode = user.helpModeActive,
                            selectedCategory = user.category
                        )
                    }

                    if (user.helpModeActive) {
                        // Se il job non è attivo, lo fa partire
                        if (heartbeatJob == null || heartbeatJob?.isActive == false) {
                            startHeartbeatLoop()
                        }
                    } else {
                        // Se non è attivo, si assicura che il loop sia spento
                        stopHeartbeatLoop()
                    }
                }
            }
        }
    }

    private fun listenForMatches() {
        viewModelScope.launch {
            // Ascolta il flusso dal Repository
            userRepository.matchEvents.collect { matchMessage ->
                // Appena arriva un evento, aggiorna la UI
                _uiState.update {
                    it.copy(
                        statusMessage = "MATCH TROVATO! $matchMessage",
                        showMatchSuccess = true // Attiva il popup
                    )
                }
            }
        }
    }

    // REGISTRAZIONE
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


    // AZIONE 1: Cambia Modalità (Helper <-> Requester)
    fun toggleHelperMode(isActive: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isHelperMode = isActive) }
            userRepository.setHelperMode(isActive)

            if(isActive) {
                startHeartbeatLoop()
            } else {
                stopHeartbeatLoop()
            }
        }
    }

    private fun startHeartbeatLoop() {
        stopHeartbeatLoop() // Sicurezza
        heartbeatJob = viewModelScope.launch(Dispatchers.IO) {
            Log.e("HandyDEBUG", "--- AVVIO LOOP HEARTBEAT ---")
            Log.i("HandyLoop", "CICLO ATTIVO: Invio Heartbeat in corso...")

            while (isActive) {
                try {
                    userRepository.sendHeartbeat()
                    Log.i("HandyLoop", "Attesa 30s...")
                } catch (e: Exception) {
                    Log.e("HandyDEBUG", "Errore nel loop: ${e.message}")
                }

                // --- IL FRENO FONDAMENTALE ---
                // Mette in pausa la coroutine per 10 secondi (10.000 millisecondi)
                delay(30_000)
            }
        }
    }

    private fun stopHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    fun dismissMatchPopup() {
        _uiState.update { it.copy(showMatchSuccess = false, statusMessage = "Pronto") }
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

    fun getChatMessages(chatId: String) = chatRepository.getMessagesFlow(chatId)

    fun sendMessage(recipientId: String, text: String) {
        viewModelScope.launch {
            chatRepository.sendMessage(recipientId, text)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as HandyApp)
                HomeVM(
                    userRepository = application.userRepository,
                    chatRepository = application.chatRepository
                )

            }
        }
    }
}