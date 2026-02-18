package com.unibo.handy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unibo.handy.HandyApp
import com.unibo.handy.data.repository.UserRepository
import com.unibo.handy.ui.UserUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UserViewModel(
    private val userRepository: UserRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(UserUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // Osserva i cambiamenti dell'utente dal DB
        viewModelScope.launch {
            userRepository.currentUserFlow.collectLatest { user ->
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        isHelperMode = user?.helpModeActive ?: false,
                        isInitialDataLoaded = true
                    )
                }
            }
        }
    }

    // --- AZIONI ATTIVE DELL'UTENTE ---
    fun toggleHelperMode(isActive: Boolean, category: String = "Generico") {
        viewModelScope.launch {
            try {
                userRepository.setHelperMode(isActive, category)
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Errore cambio modalità: ${e.message}") }
            }
        }
    }

    fun sendHelpRequest(category: String, radius: Double) {
        viewModelScope.launch {
            val user = _uiState.value.currentUser ?: return@launch

            _uiState.update { it.copy(isLoading = true, statusMessage = "Invio richiesta...") }

            try {
                userRepository.sendHelpRequest(
                    userId = user.userId,
                    category = category,
                    tolerance = radius
                )
                _uiState.update { it.copy(isLoading = false, statusMessage = "Richiesta inviata! Attendi un Helper...") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Errore invio: ${e.message}") }
            }
        }
    }

    fun updateSearchParameters(category: String, radius: Float) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                searchRadius = radius
            )
        }
    }

    // Per configurazione iniziale helper mode
    fun updateHelperDraft(category: String) {
        _uiState.update { it.copy(helperCategoryDraft = category) }
    }

    fun logout() {
        viewModelScope.launch {
            userRepository.logout()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HandyApp)
                UserViewModel(app.userRepository)
            }
        }
    }
}