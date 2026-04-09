package com.aarav.chatapplication.webrtc

import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.data.model.IceCandidateModel
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate
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

    suspend fun sendOffer(callId: String, offer: String) {
        callRef
            .child(callId)
            .child("offer")
            .setValue(offer)
            .await()
    }

    suspend fun sendAnswer(callId: String, answer: String) {
        callRef
            .child(callId)
            .child("answer")
            .setValue(answer)
            .await()
    }

    fun listenForCall(callId: String): Flow<CallModel> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val call = snapshot.getValue(CallModel::class.java)
                call?.let {
                    trySend(it)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        callRef
            .child(callId)
            .addValueEventListener(listener)

        awaitClose {
            callRef.child(callId).removeEventListener(listener)
        }
    }

    fun listenForIncomingCalls(userId: String): Flow<CallModel> = callbackFlow {

        val ref = callRef.orderByChild("receiverId").equalTo(userId)

        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                snapshot.children.forEach { child ->

                    val call = child.getValue(CallModel::class.java)

                    call?.let {
                        trySend(it)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)

        awaitClose {
            ref.removeEventListener(listener)
        }
    }

    suspend fun sendICECandidate(callId: String, candidate: IceCandidateModel) {
        val data = IceCandidateModel(
            sdp = candidate.sdp,
            sdpMid = candidate.sdpMid,
            sdpMLineIndex = candidate.sdpMLineIndex
        )

        callRef.child(callId)
            .child("candidates")
            .push()
            .setValue(data)
            .await()
    }

    fun listenForCandidate(callId: String): Flow<IceCandidateModel> = callbackFlow {

        val ref = callRef.child(callId).child("candidates")

        val listener = object : ChildEventListener {
            override fun onChildAdded(
                snapshot: DataSnapshot,
                previousChildName: String?
            ) {
                val candidate = snapshot.getValue(IceCandidateModel::class.java)
                candidate?.let {
                    trySend(it)
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
}