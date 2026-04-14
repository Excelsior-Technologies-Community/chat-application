package com.aarav.chatapplication.data.model

data class CallModel(
    val callId: String = "",
    val callerId: String = "",
    val callerName: String? = null,
    val receiverId: String = "",
    val offer: String? = null,
    val answer: String? = null,
    val ended: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class IceCandidateModel(
    val sdp: String = "",
    val sdpMid: String? = null,
    val sdpMLineIndex: Int = 0
)