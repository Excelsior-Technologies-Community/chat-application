package com.aarav.chatapplication.domain.model

data class Message(
    val id: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val status: MessageStatus
)

enum class MessageStatus {
    SENT, DELIVERED, READ
}