package com.unibo.handy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unibo.handy.data.db.entity.ChatMessagesEntity
import com.unibo.handy.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessagesEntity>>(emptyList())
    val messages = _messages.asStateFlow()

    fun loadMessages(chatId: String) {
        viewModelScope.launch {
            chatRepository.getMessagesFlow(chatId).collectLatest { list ->
                _messages.value = list
            }
        }
    }

    fun sendMessage(chatId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            chatRepository.sendMessage(chatId, text)
        }
    }

    fun acceptMatch(chatId: String) {
        viewModelScope.launch { chatRepository.acceptMatch(chatId) }
    }
}