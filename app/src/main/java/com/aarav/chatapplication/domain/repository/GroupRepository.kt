package com.aarav.chatapplication.domain.repository

import com.aarav.chatapplication.data.model.Group
import com.aarav.chatapplication.data.model.GroupPermissions
import com.aarav.chatapplication.utils.Result
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    suspend fun createGroup(
        name: String,
        creatorId: String,
        memberIds: List<String>
    ): Result<String>

    fun observeGroup(groupId: String): Flow<Group>

    fun observeUserGroups(userId: String): Flow<List<String>>

    suspend fun updateGroupName(groupId: String, newName: String): Result<Unit>

    suspend fun addMembers(groupId: String, memberIds: List<String>): Result<Unit>

    suspend fun removeMember(groupId: String, userId: String): Result<Unit>

    suspend fun leaveGroup(groupId: String, userId: String): Result<Unit>

    suspend fun promoteToAdmin(groupId: String, userId: String): Result<Unit>

    suspend fun demoteFromAdmin(groupId: String, userId: String): Result<Unit>

    suspend fun updateGroupPermissions(groupId: String, permissions: GroupPermissions): Result<Unit>

    suspend fun updateGroupDescription(groupId: String, description: String): Result<Unit>

    suspend fun pinMessage(groupId: String, messageId: String): Result<Unit>

    suspend fun unpinMessage(groupId: String): Result<Unit>
}


