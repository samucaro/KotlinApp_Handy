package com.unibo.handy.ui.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unibo.handy.data.db.entity.toDomain
import com.unibo.handy.data.repository.SecureKeyRepository
import com.unibo.handy.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Rappresenta gli stati mutuamente esclusivi della sessione utente.
 */
enum class AuthState { LOADING, LOGGED_IN, NOT_LOGGED }

/**
 * ViewModel delegato alla gestione del flusso di autenticazione e registrazione (Onboarding).
 * Applica il pattern Unidirectional Data Flow (UDF) per comunicare con l'interfaccia utente.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val secureKeyRepository: SecureKeyRepository
) : ViewModel() {
    // Stato immutabile per la UI (Campi di testo, caricamento, errori)
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    // Stato di navigazione globale (Sessione)
    private val _authState = MutableStateFlow(AuthState.LOADING)
    val authState = _authState.asStateFlow()

    init {
        checkUserSession()
    }

    /**
     * Sottoscrizione reattiva al database locale.
     * Osserva in tempo reale l'esistenza di un profilo utente per auto-loggare l'utente
     * alle successive aperture dell'app.
     */
    private fun checkUserSession() {
        // viewModelScope garantisce che la coroutine muoia quando il ViewModel viene distrutto
        viewModelScope.launch {
            // Mappatura verso il livello di dominio (Clean Architecture)
            userRepository.currentUserFlow.collect { userEntity ->
                val domainUser = userEntity?.toDomain()
                if (domainUser != null) {
                    _authState.value = AuthState.LOGGED_IN
                } else {
                    _authState.value = AuthState.NOT_LOGGED
                }
            }
        }
    }

    // --- METODI DI AGGIORNAMENTO STATO (Intents della UI) ---
    fun onUsernameChange(newValue: String) { _uiState.update { it.copy(username = newValue) } }
    fun onEmailChange(newValue: String) { _uiState.update { it.copy(email = newValue) } }
    fun onPasswordChange(newValue: String) { _uiState.update { it.copy(password = newValue) } }

    fun signUp() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // --- SIMULAZIONE TTP (Trusted Third Party) ---
                // Nel protocollo SamaritanCloud, questa fase simula il download
                // delle chiavi pubbliche di gruppo necessarie per l'omomorfismo.
                secureKeyRepository.initKeysIfEmpty()

                val s = _uiState.value
                userRepository.updateUserProfile(s.username, s.email, s.password)

                _uiState.update { it.copy(isLoading = false, isSignUpSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}