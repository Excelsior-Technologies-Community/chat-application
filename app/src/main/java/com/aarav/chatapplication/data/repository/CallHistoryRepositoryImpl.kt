package com.aarav.chatapplication.data.repository

import com.aarav.chatapplication.data.model.CallHistoryModel
import com.aarav.chatapplication.domain.repository.CallHistoryRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class CallHistoryRepositoryImpl @Inject constructor(
    private val firebaseDatabase: FirebaseDatabase
) : CallHistoryRepository {

    override suspend fun saveCallHistory(history: CallHistoryModel) {
        val ref = firebaseDatabase.reference.child("call_history").push()
        val generatedId = ref.key ?: return
        ref.setValue(history.copy(historyId = generatedId)).await()
    }

    override fun fetchCallHistory(userId: String): Flow<List<CallHistoryModel>> = callbackFlow {
        val query = firebaseDatabase.reference.child("call_history")
            .orderByChild("participants/$userId")
            .equalTo(true)
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val histories = mutableListOf<CallHistoryModel>()
                snapshot.children.forEach { child ->
                    try {
                        val history = child.getValue(CallHistoryModel::class.java)
                        if (history != null) {
                            histories.add(history)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                trySend(histories.sortedByDescending { it.timestamp })
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }
}
