package com.aarav.chatapplication.webrtc

import android.util.Log
import com.aarav.chatapplication.data.model.CallHistoryModel
import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.data.model.IceCandidateModel
import com.aarav.chatapplication.data.model.OfferModel
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class SignalingClient
@Inject constructor(
    val firebaseDatabase: FirebaseDatabase
) {
    private val callRef = firebaseDatabase.reference.child("calls")

    suspend fun createCall(call: CallModel) {
        callRef
            .child(call.callId)
            .setValue(call)
            .await()
    }

    suspend fun sendOffer(
        callId: String,
        receiverId: String,
        offer: OfferModel
    ) {
        callRef
            .child(callId)
            .child("offers")
            .child("${offer.senderId}_$receiverId")
            .setValue(offer)
            .await()
    }

    suspend fun sendAnswer(
        callId: String,
        senderId: String,
        receiverId: String,
        answer: String
    ) {
        callRef
            .child(callId)
            .child("answers")
            .child("${senderId}_$receiverId")
            .setValue(answer)
            .await()
    }

    fun listenForCall(callId: String): Flow<CallModel?> = callbackFlow {

        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }

                try {
                    val call = snapshot.getValue(CallModel::class.java)
                    trySend(call)
                } catch (e: Exception) {
                    Log.e("SIGNALING", "Failed to deserialize call ${snapshot.key}", e)
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        callRef.child(callId).addValueEventListener(listener)

        awaitClose {
            callRef.child(callId).removeEventListener(listener)
        }
    }

    fun listenForIncomingCalls(userId: String): Flow<CallModel> = callbackFlow {

        val listener = object : ChildEventListener {

            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    val call = snapshot.getValue(CallModel::class.java)
                    if (
                        call != null &&
                        call.participants.contains(userId) &&
                        call.callerId != userId &&
                        !call.ended
                    ) {
                        Log.d("SIGNALING", "Incoming call detected: ${call.callId}")
                        trySend(call)
                    }
                } catch (e: Exception) {
                    Log.e("SIGNALING", "Skipping invalid call data at ${snapshot.key}", e)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // We only care about new calls, not updates to existing ones here.
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        callRef.addChildEventListener(listener)

        awaitClose {
            callRef.removeEventListener(listener)
        }
    }

    suspend fun sendICECandidate(
        callId: String,
        receiverId: String,
        candidate: IceCandidateModel
    ) {
        val data = IceCandidateModel(
            sdp = candidate.sdp,
            sdpMid = candidate.sdpMid,
            sdpMLineIndex = candidate.sdpMLineIndex,
            senderId = candidate.senderId
        )

        callRef.child(callId)
            .child("candidates")
            .child(receiverId)
            .push()
            .setValue(data)
            .await()
    }

    fun listenForCandidate(
        callId: String,
        myUserId: String
    ): Flow<Pair<IceCandidateModel, String>> = callbackFlow {

        val ref = callRef.child(callId)
            .child("candidates")
            .child(myUserId)

        val listener = object : ChildEventListener {
            override fun onChildAdded(
                snapshot: DataSnapshot,
                previousChildName: String?
            ) {
                val candidate = snapshot.getValue(IceCandidateModel::class.java)

                candidate?.let {
                    trySend(it to it.senderId)
                }
            }

            override fun onChildChanged(
                snapshot: DataSnapshot,
                previousChildName: String?
            ) {

            }

            override fun onChildRemoved(snapshot: DataSnapshot) {

            }

            override fun onChildMoved(
                snapshot: DataSnapshot,
                previousChildName: String?
            ) {

            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }

        }

        ref.addChildEventListener(listener)

        awaitClose {
            ref.removeEventListener(listener)
        }
    }

    suspend fun endCall(callId: String) {
        callRef.child(callId)
            .child("ended")
            .setValue(true)
            .await()
    }

    fun cleanupCallData(callId: String) {
        callRef.child(callId).removeValue()
    }

    suspend fun setBusy(callId: String) {
        callRef.child(callId).child("isBusy").setValue(true).await()
        callRef.child(callId).child("ended").setValue(true).await()
    }

    suspend fun saveCallHistory(history: CallHistoryModel) {
        val rootRef = firebaseDatabase.reference.child("call_history").push()
        val generatedId = rootRef.key ?: ""
        rootRef.setValue(history.copy(historyId = generatedId)).await()
    }
}