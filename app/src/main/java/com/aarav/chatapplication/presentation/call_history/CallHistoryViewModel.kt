package com.aarav.chatapplication.presentation.call_history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.data.model.CallHistoryModel
import com.aarav.chatapplication.domain.repository.AuthRepository
import com.aarav.chatapplication.domain.repository.CallHistoryRepository
import com.aarav.chatapplication.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallHistoryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val callHistoryRepository: CallHistoryRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _callHistory = MutableStateFlow<List<CallHistoryModel>>(emptyList())
    val callHistory = _callHistory.asStateFlow()

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading = _isLoading.asStateFlow()

    private val _usersMapping = MutableStateFlow<Map<String, String>>(emptyMap())
    val usersMapping = _usersMapping.asStateFlow()

    val currentUserId = when (val result = userRepository.getCurrentUser()) {
        is com.aarav.chatapplication.utils.Result.Success -> result.data
        else -> null
    }

    init {
        fetchHistory()
        fetchUsers()
    }

    private fun fetchHistory() {
        if (currentUserId == null) return

        _isLoading.value = true

        viewModelScope.launch {
            callHistoryRepository.fetchCallHistory(currentUserId).collectLatest { histories ->
                _callHistory.value = histories
                _isLoading.value = false
            }
        }
    }

    private fun fetchUsers() {
        viewModelScope.launch {
            userRepository.getAllUsers().collectLatest { users ->
                _usersMapping.value = users.associate { (it.uid ?: "") to (it.name ?: "Unknown") }
            }
        }
    }
}