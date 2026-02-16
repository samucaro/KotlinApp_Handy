package com.unibo.handy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unibo.handy.HandyApp
import com.unibo.handy.data.repository.UserRepository
import com.unibo.handy.ui.AuthUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val userRepository: UserRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    fun onUsernameChange(newValue: String) { _uiState.update { it.copy(username = newValue) } }
    fun onEmailChange(newValue: String) { _uiState.update { it.copy(email = newValue) } }
    fun onPasswordChange(newValue: String) { _uiState.update { it.copy(password = newValue) } }


    fun signUp() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val s = _uiState.value
                userRepository.updateUserProfile(s.username, s.email, s.password, s.category)
                // Segnala alla UI che la navigazione può procedere
                _uiState.update { it.copy(isLoading = false, isSignUpSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // Factory
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HandyApp)
                AuthViewModel(app.userRepository)
            }
        }
    }
}