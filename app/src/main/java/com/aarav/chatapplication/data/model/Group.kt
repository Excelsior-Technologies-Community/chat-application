package com.aarav.chatapplication.data.model

data class Group(
    val groupId: String = "",
    val name: String = "",
    val avatar: String = "0xFF6C63FF",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val members: Map<String, Boolean> = emptyMap()
)