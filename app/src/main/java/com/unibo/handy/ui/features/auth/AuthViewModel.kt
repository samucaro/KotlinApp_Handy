package com.unibo.handy.ui.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unibo.handy.data.db.entity.toDomain
import com.unibo.handy.data.repository.SecureKeyRepository
import com.unibo.handy.data.repository.UserRepository
import com.unibo.handy.domain.crypto.PaillierEncryption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthState { LOADING, LOGGED_IN, NOT_LOGGED }

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val secureKeyRepository: SecureKeyRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    private val _authState = MutableStateFlow(AuthState.LOADING)
    val authState = _authState.asStateFlow()

    init {
        checkUserSession()
    }

    private fun checkUserSession() {
        viewModelScope.launch {
            // Controlla se nel DB esiste un utente
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

    fun onUsernameChange(newValue: String) { _uiState.update { it.copy(username = newValue) } }
    fun onEmailChange(newValue: String) { _uiState.update { it.copy(email = newValue) } }
    fun onPasswordChange(newValue: String) { _uiState.update { it.copy(password = newValue) } }

    fun signUp() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // --- SIMULAZIONE TTP (Trusted Third Party) ---
                secureKeyRepository.initKeysIfEmpty()

                val s = _uiState.value
                userRepository.updateUserProfile(s.username, s.email, s.password)

                // Segnala alla UI che la navigazione può procedere
                _uiState.update { it.copy(isLoading = false, isSignUpSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}