package com.aarav.chatapplication.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class CallModel(
    val callId: String = "",
    val callerId: String = "",
    val callerName: String? = null,
    val receiverId: String = "",
    val groupCall: Boolean = false,
    val videoCall: Boolean = false,
    val participants: Map<String, Boolean> = emptyMap(),
    val offers: Map<String, OfferModel> = emptyMap(),
    val answers: Map<String, String> = emptyMap(),
    val ended: Boolean = false,
    val isBusy: Boolean = false,
    val mediaStates: Map<String, MediaState> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

data class IceCandidateModel(
    val sdp: String = "",
    val sdpMid: String? = null,
    val sdpMLineIndex: Int = 0,
    val senderId: String = ""
)

data class CallHistoryModel(
    val historyId: String = "",
    val callerId: String = "",
    val receiverId: String = "",
    val participants: Map<String, Boolean> = emptyMap(),
    val groupCall: Boolean = false,
    val videoCall: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val duration: Long = 0,
    val status: String = ""
)

data class OfferModel(
    val sdp: String = "",
    val senderId: String = ""
)

data class MediaState(
    val muted: Boolean = false,
    val videoEnabled: Boolean = false
)