package com.aarav.chatapplication.presentation.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.domain.model.User
import com.aarav.chatapplication.domain.repository.AuthRepository
import com.aarav.chatapplication.domain.repository.ChatListRepository
import com.aarav.chatapplication.domain.repository.GroupChatRepository
import com.aarav.chatapplication.domain.repository.GroupRepository
import com.aarav.chatapplication.domain.repository.MessageRepository
import com.aarav.chatapplication.domain.repository.PresenceRepository
import com.aarav.chatapplication.domain.repository.UserRepository
import com.aarav.chatapplication.presentation.model.ChatListEntry
import com.aarav.chatapplication.presentation.model.DirectChatEntry
import com.aarav.chatapplication.presentation.model.GroupChatEntry
import com.aarav.chatapplication.utils.Result
import com.aarav.chatapplication.webrtc.SignalingClient
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class HomeScreenVM
@Inject constructor(
    val messageRepository: MessageRepository,
    val userRepository: UserRepository,
    val chatListRepository: ChatListRepository,
    val authRepository: AuthRepository,
    val presenceRepository: PresenceRepository,
    val groupRepository: GroupRepository,
    val groupChatRepository: GroupChatRepository,
    val signalingClient: SignalingClient
) : ViewModel() {
    private var _uiState: MutableStateFlow<HomeUiState> = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        getUserList()
        observeUserPresence()
    }

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser = _currentUser.asStateFlow()

    fun getCurrentUser(currentUserId: String) {
        viewModelScope.launch {
            userRepository.findUserByUserId(currentUserId)
                .collect {
                    _currentUser.value = it
                }
        }
    }

    private val _incomingCall = MutableSharedFlow<CallModel>()
    val incomingCall = _incomingCall.asSharedFlow()

    fun listenForIncomingCalls(userId: String) {
        Log.d("CALL", "Listening for incoming calls")

        viewModelScope.launch {
            signalingClient.listenForIncomingCalls(userId)
                .collect { call ->
                    if (call.offer != null && call.answer == null) {
                        _incomingCall.emit(call)
                    }
                }
        }
    }

    private fun observeUserPresence() {
        viewModelScope.launch {
            uiState
                .map { it.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    userId?.let {
                        Log.i("PRESENCE", "Presence logged")
                        presenceRepository.setupPresence(it)
                    }
                }
        }
    }

    @OptIn(FlowPreview::class)
    fun observeChatList(myId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            delay(500)

            userRepository.findUserByUserId(myId)
                .catch { }
                .collect { user ->
                    Log.d("MYTAG", "useranem : ${user.name}")
                    _uiState.update { it.copy(currentUserName = user.name ?: "You") }
                }
        }

        viewModelScope.launch {
            chatListRepository.observeUserChats(myId)
                .timeout(10.seconds)
                .catch { e ->
                    Log.i("CATCH", e.message.toString())
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            showErrorDialog = true,
                            error = "Unable to connect to servers please check your internet connection"
                        )
                    }
                }
                .collect { chatIds ->
                    if (chatIds.isEmpty()) {
                        _uiState.update {
                            it.copy(isLoading = false, directChats = emptyList())
                        }
                        return@collect
                    }

                    val chatFlows = chatIds.map { chatId ->
                        val otherUserId = chatId.split("_").first { it != myId }

                        combine(
                            chatListRepository.observeChatMeta(chatId),
                            chatListRepository.observeUnread(myId, chatId),
                            userRepository.findUserByUserId(otherUserId)
                        ) { meta, unread, user ->
                            DirectChatEntry(
                                chatId = chatId,
                                otherUserId = otherUserId,
                                otherUserName = user.name ?: "",
                                lastMessage = meta.first,
                                lastTimestamp = meta.second,
                                unreadCount = unread,
                                isOnline = false
                            ).also {
                                markChatDeliveredIfNeeded(chatId, myId, unread)
                            }
                        }
                    }

                    combine(chatFlows) { items ->
                        items.toList().sortedByDescending { it.lastTimestamp }
                    }.collect { directList ->
                        _uiState.update {
                            it.copy(isLoading = false, directChats = directList)
                        }
                    }
                }
        }

        viewModelScope.launch {
            groupRepository.observeUserGroups(myId)
                .catch { e -> Log.i("GROUP", "Error: ${e.message}") }
                .collect { groupIds ->
                    if (groupIds.isEmpty()) {
                        _uiState.update { it.copy(groupChats = emptyList()) }
                        return@collect
                    }

                    val groupFlows = groupIds.map { groupId ->
                        combine(
                            groupRepository.observeGroup(groupId),
                            groupChatRepository.observeGroupMeta(groupId),
                            groupChatRepository.observeGroupUnread(myId, groupId)
                        ) { group, meta, unread ->
                            GroupChatEntry(
                                chatId = groupId,
                                groupName = group.name,
                                lastSenderName = meta.lastSenderName,
                                lastMessage = meta.lastMessage,
                                lastTimestamp = meta.lastTimestamp,
                                unreadCount = unread,
                                memberCount = group.members.size
                            )
                        }
                    }

                    combine(groupFlows) { items ->
                        items.toList().sortedByDescending { it.lastTimestamp }
                    }.collect { groupList ->
                        _uiState.update { it.copy(groupChats = groupList) }
                    }
                }
        }
    }

    fun getUserId() {
        when (val result = userRepository.getCurrentUser()) {
            is Result.Success -> {
                _uiState.update {
                    it.copy(userId = result.data)
                }
            }

            is Result.Error -> {
                _uiState.update {
                    it.copy(
                        showErrorDialog = true,
                        error = result.message
                    )
                }
            }
        }
    }

    private fun markChatDeliveredIfNeeded(
        chatId: String,
        myId: String,
        unreadCount: Int
    ) {
        if (unreadCount <= 0) return

        viewModelScope.launch {
            messageRepository.makeChatMessagesDelivered(
                chatId = chatId,
                receiverId = myId
            )
        }
    }

    fun getUserList() {
        viewModelScope.launch {
            userRepository.getUserList()
                .catch { e ->
                    Log.i("MYTAG", e.message.toString())
                }
                .collect { user ->
                    _uiState
                        .update {
                            it.copy(userList = user)
                        }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
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
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val showErrorDialog: Boolean = false,
    val directChats: List<DirectChatEntry> = emptyList(),
    val groupChats: List<GroupChatEntry> = emptyList(),
    val userList: List<User> = emptyList(),
    val userId: String? = null,
    val currentUserName: String = "You"
) {
    val chatList: List<ChatListEntry>
        get() = (directChats + groupChats).sortedByDescending { it.lastTimestamp }
}