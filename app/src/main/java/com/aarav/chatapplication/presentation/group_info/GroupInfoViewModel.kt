package com.aarav.chatapplication.presentation.group_info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.data.model.Group
import com.aarav.chatapplication.domain.model.User
import com.aarav.chatapplication.domain.repository.GroupRepository
import com.aarav.chatapplication.domain.repository.UserRepository
import com.aarav.chatapplication.utils.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupInfoUiState())
    val uiState: StateFlow<GroupInfoUiState> = _uiState.asStateFlow()

    fun observeGroup(groupId: String) {
        viewModelScope.launch {
            groupRepository.observeGroup(groupId)
                .collect { group ->
                    _uiState.update { it.copy(group = group) }
                    observeMembersAndAvailableUsers(group.members)
                }
        }
    }

    private fun observeMembersAndAvailableUsers(members: Map<String, Boolean>) {
        viewModelScope.launch {
            userRepository.getAllUsers()
                .collect { allUsers ->
                    val groupMembers = allUsers.filter { members.containsKey(it.uid) }
                        .map { user ->
                            MemberInfo(
                                user = user,
                                isActive = members[user.uid] ?: false
                            )
                        }

                    val availableToAdd = allUsers.filter { user ->
                        val isActive = members[user.uid] ?: false
                        !isActive
                    }

                    _uiState.update {
                        it.copy(
                            members = groupMembers,
                            availableUsers = availableToAdd
                        )
                    }
                }
        }
    }

    fun updateGroupName(groupId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            groupRepository.updateGroupName(groupId, newName)
        }
    }

    fun removeMember(groupId: String, userId: String) {
        viewModelScope.launch {
            groupRepository.removeMember(groupId, userId)
        }
    }

    fun addMembers(groupId: String, userIds: List<String>) {
        viewModelScope.launch {
            groupRepository.addMembers(groupId, userIds)
        }
    }

    fun leaveGroup(groupId: String, userId: String) {
        viewModelScope.launch {
            groupRepository.leaveGroup(groupId, userId)
            _uiState.update { it.copy(hasLeftGroup = true) }
        }
    }

    fun clearLeaveState() {
        _uiState.update { it.copy(hasLeftGroup = false) }
    }
}

data class GroupInfoUiState(
    val group: Group? = null,
    val members: List<MemberInfo> = emptyList(),
    val availableUsers: List<User> = emptyList(),
    val hasLeftGroup: Boolean = false,
    val error: String? = null
)

data class MemberInfo(
    val user: User,
    val isActive: Boolean
)