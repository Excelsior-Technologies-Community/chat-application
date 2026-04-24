package com.aarav.chatapplication.domain.repository

import com.aarav.chatapplication.data.model.Message
import com.aarav.chatapplication.utils.Result
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    suspend fun sendMessage(
        chatId: String,
        senderId: String,
        receiverId: String,
        text: String
    ): Result<Unit>

    fun listenMessages(chatId: String): Flow<List<Message>>

    suspend fun makeMessageRead(
        chatId: String,
        userId: String,
        messageIds: List<String>
    )

    suspend fun makeChatMessagesDelivered(chatId: String, receiverId: String)

    fun isChatCreated(chatId: String, userId: String): Flow<Boolean>

    suspend fun deleteMessage(chatId: String, messageId: String): Result<Unit>
}