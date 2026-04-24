package com.aarav.chatapplication.data.model

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val status: String = MessageStatus.SENT.name,
    val isDeleted: Boolean = false
)


enum class MessageStatus {
    SENT, DELIVERED, READ
}