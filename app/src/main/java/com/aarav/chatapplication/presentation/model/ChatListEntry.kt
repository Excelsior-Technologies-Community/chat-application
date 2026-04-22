package com.aarav.chatapplication.presentation.model

sealed interface ChatListEntry {
    val chatId: String
    val lastMessage: String
    val lastTimestamp: Long
    val unreadCount: Int
}

data class DirectChatEntry(
    override val chatId: String,
    val otherUserId: String,
    val otherUserName: String,
    override val lastMessage: String,
    override val lastTimestamp: Long,
    override val unreadCount: Int,
    val online: Boolean
) : ChatListEntry

data class GroupChatEntry(
    override val chatId: String,
    val groupName: String,
    val lastSenderName: String,
    override val lastMessage: String,
    override val lastTimestamp: Long,
    override val unreadCount: Int,
    val memberCount: Int
) : ChatListEntry
