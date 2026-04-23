package com.aarav.chatapplication.domain.model

data class Group(
    val groupId: String,
    val name: String,
    val avatar: String,
    val createdBy: String,
    val createdAt: Long,
    val memberIds: List<String>
)