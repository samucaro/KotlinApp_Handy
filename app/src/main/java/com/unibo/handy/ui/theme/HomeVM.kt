package com.unibo.handy.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.unibo.handy.HandyApp
import com.unibo.handy.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeVM(private val userRepository: UserRepository) : ViewModel() {
    private val _userId = MutableStateFlow<String>("Loading...")
    val userId: StateFlow<String> = _userId.asStateFlow()

    init {
        loadUser()
    }


    private fun loadUser() {
        viewModelScope.launch {
            userRepository.currentUserFlow.collect { user ->
                if (user == null) {
                    // SE IL DB È VUOTO -> SIMULA REGISTRAZIONE (TEST)
                    _userId.value = "Creazione utente test..."
                    simulateRegistration()
                } else {
                    _userId.value = user.userId
                }
            }
        }
    }

    private suspend fun simulateRegistration() {
        // Chiamo il repository per salvare su DB e avvisare il server
        userRepository.updateUserProfile(
            username = "johnmclean",
            email = "john.mclean@examplepetstore.com",
            psw = "test",
            category = "Test"
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