package com.aarav.chatapplication.presentation.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.data.model.Message
import com.aarav.chatapplication.data.model.MessageStatus
import com.aarav.chatapplication.data.model.Presence
import com.aarav.chatapplication.domain.model.User
import com.aarav.chatapplication.domain.repository.MessageRepository
import com.aarav.chatapplication.domain.repository.PresenceRepository
import com.aarav.chatapplication.domain.repository.TypingRepository
import com.aarav.chatapplication.domain.repository.UserRepository
import com.aarav.chatapplication.utils.Result
import com.aarav.chatapplication.webrtc.SignalingClient
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel
@Inject constructor(
    val userRepository: UserRepository,
    val messageRepository: MessageRepository,
    val typingRepository: TypingRepository,
    val presenceRepository: PresenceRepository,
) : ViewModel() {

    private var _uiState: MutableStateFlow<ChatUiState> = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentChatId: String? = null
    private var currentUserId: String? = null

    fun observePresence(otherUserId: String) {
        viewModelScope.launch {
            presenceRepository.observePresence(otherUserId)
                .collect { presence ->
                    _uiState.update {
                        it.copy(presence = presence)
                    }
                }
        }
    }

    fun observeTyping() {
        val chatId = currentChatId ?: return
        val myId = currentUserId ?: return

        viewModelScope.launch {
            typingRepository.observeTyping(chatId)
                .collect { typingUser ->
                    _uiState.update {
                        it.copy(
                            isOtherUserTyping = typingUser.any {
                                it != myId
                            }
                        )
                    }
                }
        }
    }

    fun onTypingStarted() {
        val chatId = currentChatId ?: return
        val myId = currentUserId ?: return

        viewModelScope.launch {
            typingRepository.setTyping(chatId, myId)
        }
    }

    fun onTypingStopped() {
        val chatId = currentChatId ?: return
        val myId = currentUserId ?: return

        viewModelScope.launch {
            typingRepository.clearTyping(chatId, myId)
        }
    }

    fun observeMessages(chatId: String, myId: String) {
        currentChatId = chatId
        currentUserId = myId

        viewModelScope.launch {
            observeTyping()

            messageRepository.listenMessages(chatId)
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message,
                            showErrorDialog = true
                        )
                    }
                }
                .collect { messageList ->
                    _uiState.update {
                        it.copy(messages = messageList)
                    }
                    autoMarksRead(messageList)
                }
        }
    }

    fun sendMessages(receiverId: String, text: String) {
        val chatId = currentChatId ?: return
        val senderId = currentUserId ?: return

        if (text.isBlank()) {
            _uiState.update {
                it.copy(messageError = "Message cannot be blank")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isSending = true)
            }

            when (val result = messageRepository.sendMessage(
                chatId, senderId, receiverId, text
            )) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(isSending = false)
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            error = result.message,
                            showErrorDialog = true
                        )
                    }
                }
            }
        }
    }

    fun autoMarksRead(messages: List<Message>) {
        val chatId = currentChatId ?: return
        val myId = currentUserId ?: return

        viewModelScope.launch {
            val unread = messages.filter {
                it.senderId != myId &&
                        it.status != MessageStatus.READ.name
            }

            if (unread.isNotEmpty()) {
                messageRepository.makeMessageRead(
                    chatId,
                    myId,
                    unread.map { it.messageId }
                )
            }
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(
                showErrorDialog = false,
                error = null
            )
        }
    }

    fun getUser(userId: String) {
        viewModelScope.launch {
            userRepository.findUserByUserId(userId)
                .collect { user ->
                    _uiState.update {
                        it.copy(user = user)
                    }
                }
        }
    }

    fun isChatCreated(chatId: String, userId: String) {
        viewModelScope.launch {
            messageRepository.isChatCreated(chatId, userId)
                .collect {
                    _uiState.update { state ->
                        state.copy(isChatCreated = it)
                    }
                }
        }
    }

    fun deleteMessage(messageId: String) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            messageRepository.deleteMessage(chatId, messageId)
        }
    }
}


data class ChatUiState(
    val error: String? = null,
    val messageError: String? = null,
    val showErrorDialog: Boolean = false,
    val messages: List<Message> = emptyList(),
    val isSending: Boolean = false,
    val isOtherUserTyping: Boolean = false,
    val presence: Presence? = null,
    val user: User? = null,
    val isChatCreated: Boolean = false
)