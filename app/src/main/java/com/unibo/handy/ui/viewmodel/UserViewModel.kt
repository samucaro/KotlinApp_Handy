package com.unibo.handy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unibo.handy.data.db.entity.toDomain
import com.unibo.handy.data.network.WebSocketManager
import com.unibo.handy.data.repository.UserRepository
import com.unibo.handy.ui.UserUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val webSocketManager: WebSocketManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(UserUiState())
    val uiState = _uiState.asStateFlow()

    val networkStatus = webSocketManager.networkStatus

    init {
        // Osserva i cambiamenti dell'utente dal DB
        viewModelScope.launch {
            userRepository.currentUserFlow.collectLatest { userEntity ->
                // Mappa l'Entity nel modello di dominio
                val domainUser = userEntity?.toDomain()

                _uiState.update {
                    it.copy(
                        currentUser = domainUser,
                        isHelperMode = domainUser?.helpModeActive ?: false,
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

    fun retryConnection() {
        viewModelScope.launch {
            webSocketManager.resetAndReconnect()
        }
    }
}