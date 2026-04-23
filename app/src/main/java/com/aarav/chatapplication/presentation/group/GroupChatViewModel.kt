package com.aarav.chatapplication.presentation.group

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.data.model.Group
import com.aarav.chatapplication.data.model.GroupMessage
import com.aarav.chatapplication.data.model.MessageStatus
import com.aarav.chatapplication.domain.repository.GroupChatRepository
import com.aarav.chatapplication.domain.repository.GroupRepository
import com.aarav.chatapplication.utils.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val groupChatRepository: GroupChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupChatUiState())
    val uiState: StateFlow<GroupChatUiState> = _uiState.asStateFlow()

    private var currentGroupId: String? = null
    private var currentUserId: String? = null

    fun observeGroup(groupId: String) {
        viewModelScope.launch {
            groupRepository.observeGroup(groupId)
                .catch { e ->
                    Log.e("GroupChat", "Error observing group", e)
                }
                .collect { group ->
                    _uiState.update {
                        it.copy(group = group)
                    }
                }
        }
    }

    fun observeMessages(groupId: String, myId: String) {
        currentGroupId = groupId
        currentUserId = myId

        viewModelScope.launch {
            observeTyping()

            groupChatRepository.listenGroupMessages(groupId)
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message,
                            showErrorDialog = true
                        )
                    }
                }
                .collect { messages ->
                    _uiState.update {
                        it.copy(messages = messages)
                    }

                    if (messages.any { it.senderId != myId }) {
                        groupChatRepository.markGroupMessagesRead(groupId, myId)
                    }
                }
        }
    }

    fun sendMessage(text: String) {
        val groupId = currentGroupId ?: return
        val senderId = currentUserId ?: return
        val group = _uiState.value.group ?: return

        if (group.members[senderId] != true) {
            _uiState.update { it.copy(messageError = "You are no longer a member of this group") }
            return
        }

        if (text.isBlank()) {
            _uiState.update { it.copy(messageError = "Message cannot be blank") }
            return
        }

        val senderName = _uiState.value.senderName

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }

            val activeMemberIds = group.members.filter { it.value }.keys.toList()

            when (val result = groupChatRepository.sendGroupMessage(
                groupId = groupId,
                senderId = senderId,
                senderName = senderName,
                memberIds = activeMemberIds,
                text = text
            )) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSending = false) }
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

    fun setSenderName(name: String) {
        _uiState.update { it.copy(senderName = name) }
    }

    fun onTypingStarted() {
        val groupId = currentGroupId ?: return
        val myId = currentUserId ?: return

        viewModelScope.launch {
            groupChatRepository.setGroupTyping(groupId, myId)
        }
    }

    fun onTypingStopped() {
        val groupId = currentGroupId ?: return
        val myId = currentUserId ?: return

        viewModelScope.launch {
            groupChatRepository.clearGroupTyping(groupId, myId)
        }
    }

    private fun observeTyping() {
        val groupId = currentGroupId ?: return
        val myId = currentUserId ?: return

        viewModelScope.launch {
            groupChatRepository.observeGroupTyping(groupId)
                .collect { typingUsers ->
                    val othersTyping = typingUsers.filter { it != myId }
                    _uiState.update {
                        it.copy(typingUserIds = othersTyping)
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(
                showErrorDialog = false,
                error = null,
                messageError = null
            )
        }
    }
}

data class GroupChatUiState(
    val error: String? = null,
    val messageError: String? = null,
    val showErrorDialog: Boolean = false,
    val messages: List<GroupMessage> = emptyList(),
    val isSending: Boolean = false,
    val group: Group? = null,
    val senderName: String = "",
    val typingUserIds: List<String> = emptyList()
)