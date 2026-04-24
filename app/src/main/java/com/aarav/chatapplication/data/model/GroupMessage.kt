package com.aarav.chatapplication.data.model

data class GroupMessage(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val status: String = MessageStatus.SENT.name,
    val isDeleted: Boolean = false,
    val deletedBy: String? = null
)