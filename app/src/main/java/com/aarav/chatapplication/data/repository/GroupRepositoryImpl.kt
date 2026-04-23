package com.aarav.chatapplication.data.repository

import com.aarav.chatapplication.data.model.Group
import com.aarav.chatapplication.data.remote.FirebasePaths
import com.aarav.chatapplication.domain.repository.GroupRepository
import com.aarav.chatapplication.utils.Result
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class GroupRepositoryImpl @Inject constructor(
    private val firebaseDatabase: FirebaseDatabase
) : GroupRepository {

    private val rootRef = firebaseDatabase.reference

    override suspend fun createGroup(
        name: String,
        creatorId: String,
        memberIds: List<String>
    ): Result<String> {
        return try {
            val groupRef = rootRef.child("groups").push()
            val groupId = groupRef.key ?: throw Exception("Group id is null")

            val allMembers = (memberIds + creatorId).distinct()
            val membersMap = allMembers.associateWith { true }

            val group = Group(
                groupId = groupId,
                name = name,
                avatar = "0xFF6C63FF",
                createdBy = creatorId,
                createdAt = System.currentTimeMillis(),
                members = membersMap
            )

            val updates = hashMapOf<String, Any>(
                FirebasePaths.group(groupId) to group
            )

            allMembers.forEach { userId ->
                updates["user_groups/$userId/$groupId"] = true
            }

            rootRef.updateChildren(updates).await()
            Result.Success(groupId)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to create group")
        }
    }

    override fun observeGroup(groupId: String): Flow<Group> = callbackFlow {
        val ref = rootRef.child(FirebasePaths.group(groupId))

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val group = snapshot.getValue(Group::class.java)
                group?.let { trySend(it) }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override fun observeUserGroups(userId: String): Flow<List<String>> = callbackFlow {
        val ref = rootRef.child(FirebasePaths.userGroups(userId))

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val groupIds = snapshot.children.mapNotNull { it.key }
                trySend(groupIds)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun updateGroupName(groupId: String, newName: String): Result<Unit> {
        return try {
            rootRef.child(FirebasePaths.group(groupId)).child("name").setValue(newName).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to update group name")
        }
    }

    override suspend fun addMembers(groupId: String, memberIds: List<String>): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any>()
            memberIds.forEach { userId ->
                updates["${FirebasePaths.group(groupId)}/members/$userId"] = true
                updates["${FirebasePaths.userGroups(userId)}/$groupId"] = true
            }
            rootRef.updateChildren(updates).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to add members")
        }
    }

    override suspend fun removeMember(groupId: String, userId: String): Result<Unit> {
        return try {
            rootRef.child(FirebasePaths.group(groupId)).child("members").child(userId).setValue(false).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to remove member")
        }
    }

    override suspend fun leaveGroup(groupId: String, userId: String): Result<Unit> {
        return try {
            rootRef.child(FirebasePaths.group(groupId)).child("members").child(userId).setValue(false).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to leave group")
        }
    }
}