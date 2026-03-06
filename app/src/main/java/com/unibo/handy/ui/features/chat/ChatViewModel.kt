package com.unibo.handy.ui.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unibo.handy.data.db.entity.ChatMessagesEntity
import com.unibo.handy.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel delegato alla gestione della messaggistica in tempo reale (Chat).
 * Collega l'interfaccia utente al database locale (Room) per mostrare lo storico
 * e instradare i nuovi messaggi verso l'infrastruttura di rete.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _currentChatId = MutableStateFlow<String?>(null)

    /**
     * Flusso di stato pubblico (StateFlow) osservato dalla UI.
     * Utilizza flatMapLatest per garantire che ci sia sempre e solo UN flusso di lettura attivo dal DB.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessagesEntity>> = _currentChatId
        .flatMapLatest { chatId ->
            if (chatId == null) {
                // Se non c'è nessuna chat selezionata, emette una lista vuota
                flowOf(emptyList())
            } else {
                // Si "abbona" ai cambiamenti del database per quello specifico ID
                chatRepository.getMessagesFlow(chatId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun loadMessages(chatId: String) {
        _currentChatId.value = chatId
    }

    fun sendMessage(chatId: String, text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            chatRepository.sendMessage(chatId, text)
        }
    }
}