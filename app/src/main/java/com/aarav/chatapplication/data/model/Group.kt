package com.aarav.chatapplication.data.model

data class Group(
    val groupId: String = "",
    val name: String = "",
    val avatar: String = "0xFF6C63FF",
    val description: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val members: Map<String, Boolean> = emptyMap(),
    val admins: List<String> = emptyList(),
    val permissions: GroupPermissions = GroupPermissions(),
    val pinnedMessageId: String? = null
)

data class GroupPermissions(
    val adminsOnlyEditInfo: Boolean = false,
    val adminsOnlyAddMembers: Boolean = false
)