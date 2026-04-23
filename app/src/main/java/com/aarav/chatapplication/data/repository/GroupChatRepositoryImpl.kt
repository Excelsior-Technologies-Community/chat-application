package com.aarav.chatapplication.data.repository

import com.aarav.chatapplication.data.model.GroupMessage
import com.aarav.chatapplication.data.model.GroupMeta
import com.aarav.chatapplication.data.model.MessageStatus
import com.aarav.chatapplication.data.remote.FirebasePaths
import com.aarav.chatapplication.domain.repository.GroupChatRepository
import com.aarav.chatapplication.utils.Result
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class GroupChatRepositoryImpl @Inject constructor(
    private val firebaseDatabase: FirebaseDatabase
) : GroupChatRepository {

    private val rootRef = firebaseDatabase.reference

    override suspend fun sendGroupMessage(
        groupId: String,
        senderId: String,
        senderName: String,
        memberIds: List<String>,
        text: String
    ): Result<Unit> {
        return try {
            val messageRef = rootRef.child(FirebasePaths.groupMessages(groupId)).push()
            val messageId = messageRef.key ?: throw Exception("Message id is null")

            val timestamp = System.currentTimeMillis()

            val message = GroupMessage(
                messageId = messageId,
                senderId = senderId,
                senderName = senderName,
                text = text,
                timestamp = timestamp,
                status = MessageStatus.SENT.name
            )

            val meta = GroupMeta(
                lastMessage = text,
                lastSenderName = senderName,
                lastTimestamp = timestamp
            )

            val updates = hashMapOf<String, Any>(
                FirebasePaths.groupMessage(groupId, messageId) to message,
                FirebasePaths.groupMeta(groupId) to meta
            )

            memberIds.filter { it != senderId }.forEach { memberId ->
                updates[FirebasePaths.groupUnread(memberId, groupId)] =
                    ServerValue.increment(1)
            }

            rootRef.updateChildren(updates).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to send group message")
        }
    }

    override fun listenGroupMessages(groupId: String): Flow<List<GroupMessage>> = callbackFlow {
        val ref = rootRef.child(FirebasePaths.groupMessages(groupId))

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull {
                    it.getValue(GroupMessage::class.java)
                }.sortedBy { it.timestamp }

                trySend(messages)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override fun observeGroupMeta(groupId: String): Flow<GroupMeta> = callbackFlow {
        val ref = rootRef.child(FirebasePaths.groupMeta(groupId))

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val meta = snapshot.getValue(GroupMeta::class.java)
                    ?: GroupMeta()
                trySend(meta)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override fun observeGroupUnread(userId: String, groupId: String): Flow<Int> = callbackFlow {
        val ref = rootRef.child(FirebasePaths.groupUnread(userId, groupId))

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val unread = (snapshot.value as? Long)?.toInt() ?: 0
                trySend(unread)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun markGroupMessagesRead(groupId: String, userId: String) {
        try {
            rootRef.child(FirebasePaths.groupUnread(userId, groupId))
                .setValue(0)
                .await()
        } catch (_: Exception) { }
    }

    override suspend fun setGroupTyping(groupId: String, userId: String) {
        try {
            rootRef.child(FirebasePaths.groupTyping(groupId, userId))
                .setValue(true)
                .await()
        } catch (_: Exception) { }
    }

    override suspend fun clearGroupTyping(groupId: String, userId: String) {
        try {
            rootRef.child(FirebasePaths.groupTyping(groupId, userId))
                .removeValue()
                .await()
        } catch (_: Exception) { }
    }

    override fun observeGroupTyping(groupId: String): Flow<List<String>> = callbackFlow {
        val ref = rootRef.child("group_typing/$groupId")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val typingUsers = snapshot.children.mapNotNull { it.key }
                trySend(typingUsers)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}