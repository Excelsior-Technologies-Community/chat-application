package com.aarav.chatapplication.data.repository

import com.aarav.chatapplication.domain.model.User
import com.aarav.chatapplication.domain.repository.UserRepository
import com.aarav.chatapplication.utils.Result
import com.aarav.chatapplication.utils.UserData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    val firebaseAuth: FirebaseAuth,
    val firebaseDatabase: FirebaseDatabase
) : UserRepository {

    private val userReference = firebaseDatabase.getReference("users")

    override suspend fun storeUserInfo(user: User): Result<Unit> {
        return try {
            userReference.child(user.uid.orEmpty())
                .setValue(user)
                .await()

            Result.Success(Unit)
        } catch (e: IOException) {
            Result.Error(e.message ?: "Failed to store user data")
        }
    }

    override fun getCurrentUser(): Result<String> {
        val user = firebaseAuth.currentUser
        return if (user != null) {
            Result.Success(user.uid)
        } else {
            Result.Error("User not logged in")
        }
    }

    override suspend fun writeTestData() {
        val userList = UserData.userList

        for (user in userList) {
            user.uid?.let {
                userReference.child(it)
                    .setValue(user)
            }
        }
    }

    override fun getUserList(): Flow<List<User>> = callbackFlow {
        val currentUserId = firebaseAuth.currentUser?.uid

        if (currentUserId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = snapshot.children
                    .mapNotNull { it.getValue(User::class.java) }
                    .filter { it.uid != currentUserId }

                trySend(users).isSuccess
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        userReference.addValueEventListener(listener)
        awaitClose { userReference.removeEventListener(listener) }
    }

    override fun getAllUsers(): Flow<List<User>> = callbackFlow {

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = snapshot.children.mapNotNull {
                    it.getValue(User::class.java)
                }
                trySend(users)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        userReference.addValueEventListener(listener)

        awaitClose {
            userReference.removeEventListener(listener)
        }
    }

    override fun findUserByUserId(userId: String): Flow<User> =
        callbackFlow {
            val snapshot = userReference.child(userId)
                .get().await()

            val user = snapshot.getValue(User::class.java)
            user?.let {
                trySend(it)
            }

            awaitClose { }
        }
}