package com.aarav.chatapplication.presentation.chat_info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.domain.model.User
import com.aarav.chatapplication.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatInfoViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatInfoUiState())
    val uiState: StateFlow<ChatInfoUiState> = _uiState.asStateFlow()

    fun getUser(userId: String) {
        viewModelScope.launch {
            userRepository.findUserByUserId(userId)
                .collect { user ->
                    _uiState.update { it.copy(user = user) }
                }
        }
    }
}

data class ChatInfoUiState(
    val user: User? = null,
    val isMuted: Boolean = false,
    val isBlocked: Boolean = false
)