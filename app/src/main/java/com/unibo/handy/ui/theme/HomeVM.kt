package com.unibo.handy.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unibo.handy.HandyApp
import com.unibo.handy.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeVM(private val userRepository: UserRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadUser()
        listenForMatches()
    }

    private fun loadUser() {
        viewModelScope.launch {
            userRepository.currentUserFlow.collect { user ->
                if (user == null) {
                    _uiState.update { it.copy(userId = "Creazione utente test...") }
                    simulateRegistration()
                } else {
                    _uiState.update {
                        it.copy(
                            userId = user.userId,
                            isHelperMode = user.helpModeActive,
                            selectedCategory = user.category
                        )
                    }
                }
            }
        }
    }

    fun dismissMatchPopup() {
        _uiState.update { it.copy(showMatchSuccess = false, statusMessage = "Pronto") }
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

    // AZIONE 1: Cambia Modalità (Helper <-> Requester)
    fun toggleHelperMode(isActive: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isHelperMode = isActive) }
            userRepository.setHelperMode(isActive)

            if(isActive) {
                userRepository.sendHeartbeat()
            }
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

    private suspend fun simulateRegistration() {
        userRepository.updateUserProfile(
            username = "NewUser",
            email = "test@handy.com",
            psw = "1234",
            category = "Generico"
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as HandyApp)
                HomeVM(application.userRepository)
            }
        }
    }
}