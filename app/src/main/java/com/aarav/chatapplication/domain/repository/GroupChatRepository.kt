package com.aarav.chatapplication.domain.repository

import com.aarav.chatapplication.data.model.GroupMessage
import com.aarav.chatapplication.data.model.GroupMeta
import com.aarav.chatapplication.utils.Result
import kotlinx.coroutines.flow.Flow

interface GroupChatRepository {
    suspend fun sendGroupMessage(
        groupId: String,
        senderId: String,
        senderName: String,
        memberIds: List<String>,
        text: String
    ): Result<Unit>

    fun listenGroupMessages(groupId: String): Flow<List<GroupMessage>>

    fun observeGroupMeta(groupId: String): Flow<GroupMeta>

    fun observeGroupUnread(userId: String, groupId: String): Flow<Int>

    suspend fun markGroupMessagesRead(groupId: String, userId: String)

    suspend fun setGroupTyping(groupId: String, userId: String)

    suspend fun clearGroupTyping(groupId: String, userId: String)

    fun observeGroupTyping(groupId: String): Flow<List<String>>
}