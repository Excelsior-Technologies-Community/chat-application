package com.aarav.chatapplication.presentation.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.domain.model.User
import com.aarav.chatapplication.domain.repository.GroupRepository
import com.aarav.chatapplication.domain.repository.UserRepository
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
class CreateGroupViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateGroupUiState())
    val uiState: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()

    init {
        loadUsers()
    }

    private fun loadUsers() {
        viewModelScope.launch {
            userRepository.getUserList()
                .catch { e ->
                    _uiState.update {
                        it.copy(error = e.message)
                    }
                }
                .collect { users ->
                    _uiState.update {
                        it.copy(userList = users)
                    }
                }
        }
    }

    fun toggleUserSelection(userId: String) {
        _uiState.update { state ->
            val currentSelected = state.selectedUserIds.toMutableSet()
            if (currentSelected.contains(userId)) {
                currentSelected.remove(userId)
            } else {
                currentSelected.add(userId)
            }
            state.copy(selectedUserIds = currentSelected)
        }
    }

    fun updateGroupName(name: String) {
        _uiState.update { it.copy(groupName = name) }
    }

    fun createGroup(creatorId: String) {
        val state = _uiState.value

        if (state.groupName.isBlank()) {
            _uiState.update { it.copy(error = "Group name cannot be empty") }
            return
        }

        if (state.selectedUserIds.isEmpty()) {
            _uiState.update { it.copy(error = "Select at least one member") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true) }

            when (val result = groupRepository.createGroup(
                name = state.groupName,
                creatorId = creatorId,
                memberIds = state.selectedUserIds.toList()
            )) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            createdGroupId = result.data
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class CreateGroupUiState(
    val userList: List<User> = emptyList(),
    val selectedUserIds: Set<String> = emptySet(),
    val groupName: String = "",
    val isCreating: Boolean = false,
    val createdGroupId: String? = null,
    val error: String? = null
)