package com.aarav.chatapplication.presentation.call

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.data.model.CallHistoryModel
import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.data.model.IceCandidateModel
import com.aarav.chatapplication.data.model.OfferModel
import com.aarav.chatapplication.webrtc.CallStateManager
import com.aarav.chatapplication.webrtc.SignalingClient
import com.aarav.chatapplication.webrtc.WebRTCClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.webrtc.VideoTrack
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val signalingClient: SignalingClient,
    private val webRTCClient: WebRTCClient,
    private val callStateManager: CallStateManager
) : ViewModel() {

    private var isCaller = false

    private val handledOfferKeys = mutableSetOf<String>()
    private val handledAnswerKeys = mutableSetOf<String>()

    private val connectedPeers = mutableSetOf<String>()

    private var timerStarted = false

    private var activeCallId: String? = null
    private var activeCallerId: String = ""
    private var activeReceiverId: String = ""

    private var myUserId: String = ""
    private var isAnswered = false

    @Volatile
    private var isEnding = false

    val callState = callStateManager.callState

    private val _callEnded = MutableStateFlow(false)
    val callEnded = _callEnded.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()

    private val _callTime = MutableStateFlow(0)
    val callTime = _callTime.asStateFlow()

    private val _events = Channel<UiEvent>()
    val events = _events.receiveAsFlow()

    val tracks = webRTCClient.allTracks
    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack = _localVideoTrack.asStateFlow()
    val eglContext = webRTCClient.eglContext

    private var connectionJob: Job? = null
    private var signalingJob: Job? = null
    private var iceOutgoingJob: Job? = null
    private var iceIncomingJob: Job? = null
    private var timerJob: Job? = null
    private var timeoutJob: Job? = null

    init {
        connectionJob = viewModelScope.launch {
            webRTCClient.connectionState.collect { state ->
                if (!isEnding) {
                    Log.d("CONNECTION", "STATE: $state")
                    when (state) {
                        "CONNECTED" -> {
                            callStateManager.updateState("CONNECTED")
                            startTimer()
                        }
                        "DISCONNECTED" -> callStateManager.updateState("DISCONNECTED")
                        "FAILED" -> callStateManager.updateState("FAILED")
                        "NEW" -> {}
                        else -> callStateManager.updateState("CONNECTING")
                    }
                }
            }
        }
    }

    fun startCall(call: CallModel, myUserId: String) {
        this.myUserId = myUserId
        isCaller = true
        resetState()

        activeCallId = call.callId
        callStateManager.activeCallId = call.callId
        activeCallerId = call.callerId
        activeReceiverId = call.participants.firstOrNull { it != myUserId } ?: ""

        callStateManager.updateState("CALLING")

        viewModelScope.launch {
            webRTCClient.init()
            webRTCClient.startLocalVideo()
            _localVideoTrack.value = webRTCClient.localVideoTrack

            signalingClient.createCall(call)

            val others = call.participants.filter { it != myUserId }
            others.forEach { userId -> initiateOfferTo(call.callId, userId) }

            startObservers(call.callId)
        }

        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(30_000)
            if (!isAnswered && !isEnding) {
                callStateManager.updateState("MISSED")
                activeCallId?.let { endCall(it) }
            }
        }
    }

    fun receiveCall(callId: String, myUserId: String) {
        this.myUserId = myUserId
        isCaller = false
        resetState()

        activeCallId = callId
        callStateManager.activeCallId = callId
        activeReceiverId = myUserId

        callStateManager.updateState("RECEIVING")

        viewModelScope.launch {
            webRTCClient.init()
            webRTCClient.startLocalVideo()
            _localVideoTrack.value = webRTCClient.localVideoTrack
            startObservers(callId)
        }
    }

    private fun resetState() {
        handledOfferKeys.clear()
        handledAnswerKeys.clear()
        connectedPeers.clear()
        isAnswered = false
        isEnding = false
        timerStarted = false
        _callTime.value = 0
        _isMuted.value = false
        _callEnded.value = false
        timerJob?.cancel()
    }

    private fun initiateOfferTo(callId: String, userId: String) {
        if (connectedPeers.contains(userId)) return
        connectedPeers.add(userId)

        webRTCClient.createPeerConnection(userId)
        webRTCClient.createOffer(userId) { sdp ->
            viewModelScope.launch {
                if (!isEnding) {
                    Log.d("SIGNALING", "Sending offer from $myUserId to $userId")
                    signalingClient.sendOffer(
                        callId,
                        userId,
                        OfferModel(sdp.description, myUserId)
                    )
                }
            }
        }
    }

    private fun startObservers(callId: String) {
        signalingJob?.cancel()
        iceOutgoingJob?.cancel()
        iceIncomingJob?.cancel()

        signalingJob = viewModelScope.launch {
            signalingClient.listenForCall(callId).collect { call ->
                if (isEnding) return@collect
                if (call == null) return@collect

                if (activeCallerId.isEmpty()) activeCallerId = call.callerId

                if (call.ended) {
                    if (call.isBusy) {
                        finishCall(call.callId, "BUSY")
                    } else if (!isAnswered && isCaller) {
                        finishCall(call.callId, "REJECTED")
                    } else {
                        finishCall(call.callId, "ENDED")
                    }
                    return@collect
                }

                call.participants.forEach { peerId ->
                    if (peerId == myUserId) return@forEach
                    if (connectedPeers.contains(peerId)) return@forEach

                    val shouldIOffer = isCaller || myUserId < peerId
                    if (shouldIOffer) {
                        initiateOfferTo(callId, peerId)
                    }
                }

                call.offers.forEach { (key, offer) ->
                    if (!key.endsWith("_$myUserId")) return@forEach
                    if (handledOfferKeys.contains(key)) return@forEach
                    handledOfferKeys.add(key)

                    val senderId = offer.senderId
                    Log.d("SIGNALING", "Processing offer from $senderId to me ($myUserId)")

                    if (!isAnswered) {
                        isAnswered = true
                        timeoutJob?.cancel()
                    }
                    callStateManager.updateState("CONNECTING")

                    if (!connectedPeers.contains(senderId)) {
                        connectedPeers.add(senderId)
                        webRTCClient.createPeerConnection(senderId)
                    }

                    webRTCClient.onRemoteOfferReceived(senderId, offer.sdp) { answer ->
                        viewModelScope.launch {
                            if (!isEnding) {
                                Log.d("SIGNALING", "Sending answer from $myUserId to $senderId")
                                signalingClient.sendAnswer(callId, myUserId, senderId, answer.description)
                            }
                        }
                    }
                }

                call.answers.forEach { (key, answer) ->
                    if (!key.endsWith("_$myUserId")) return@forEach
                    if (handledAnswerKeys.contains(key)) return@forEach
                    handledAnswerKeys.add(key)

                    val senderId = key.removeSuffix("_$myUserId")
                    Log.d("SIGNALING", "Processing answer from $senderId to me ($myUserId)")

                    if (!isAnswered) {
                        isAnswered = true
                        timeoutJob?.cancel()
                    }
                    callStateManager.updateState("CONNECTING")
                    webRTCClient.onAnswerReceived(senderId, answer)
                }
            }
        }

        iceOutgoingJob = viewModelScope.launch {
            webRTCClient.iceCandidateFlow.collect { (candidate, userId) ->
                if (!isEnding) {
                    signalingClient.sendICECandidate(
                        callId,
                        userId,
                        IceCandidateModel(
                            sdp = candidate.sdp,
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex,
                            senderId = myUserId
                        )
                    )
                }
            }
        }

        iceIncomingJob = viewModelScope.launch {
            signalingClient.listenForCandidate(callId, myUserId).collect { (candidate, fromUserId) ->
                webRTCClient.addIceCandidate(fromUserId, candidate)
            }
        }
    }

    private fun startTimer() {
        if (timerStarted) return
        timerStarted = true
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _callTime.value++
            }
        }
    }

    fun toggleCamera() {
        webRTCClient.switchCamera()
    }

    fun toggleMute() {
        val newState = !_isMuted.value
        _isMuted.value = newState
        webRTCClient.toggleMute(newState)
    }

    fun endCall(callId: String) {
        if (isEnding) return

        val currentStatus = callStateManager.callState.value
        val finalStatus = when {
            currentStatus == "MISSED" -> "missed"
            currentStatus == "BUSY" -> "busy"
            currentStatus == "REJECTED" -> "rejected"
            !isAnswered -> "missed"
            else -> "completed"
        }
        saveHistoryIfNeeded(finalStatus)

        isEnding = true
        viewModelScope.launch {
            try {
                signalingClient.endCall(callId)
                webRTCClient.closeConnection()
                callStateManager.updateState(
                    if (currentStatus in listOf("MISSED", "BUSY", "REJECTED")) currentStatus else "ENDED"
                )
                _callEnded.value = true
            } catch (e: Exception) {
                Log.e("CALL", "Error ending call", e)
            }

            _events.trySend(UiEvent.EndCall)
            _callEnded.value = true

            signalingJob?.cancel()
            signalingJob = null
            iceOutgoingJob?.cancel()
            iceOutgoingJob = null
            iceIncomingJob?.cancel()
            iceIncomingJob = null
            timerJob?.cancel()
            timerJob = null

            delay(1000)
            signalingClient.cleanupCallData(callId)
            activeCallId = null
            callStateManager.updateState("IDLE")
            isEnding = false
        }
    }

    private fun finishCall(callId: String, statusString: String = "ENDED") {
        if (isEnding) return

        val finalStatus = when (statusString) {
            "BUSY" -> "busy"
            "REJECTED" -> "rejected"
            "MISSED" -> "missed"
            else -> if (isAnswered) "completed" else "missed"
        }
        saveHistoryIfNeeded(finalStatus)

        isEnding = true
        callStateManager.updateState(statusString)
        viewModelScope.launch {
            webRTCClient.closeConnection()
            _events.trySend(UiEvent.EndCall)
            _callEnded.value = true

            signalingJob?.cancel()
            signalingJob = null
            iceOutgoingJob?.cancel()
            iceOutgoingJob = null
            iceIncomingJob?.cancel()
            iceIncomingJob = null
            timerJob?.cancel()
            timerJob = null
            timeoutJob?.cancel()
            timeoutJob = null

            delay(1000)
            signalingClient.cleanupCallData(callId)
            activeCallId = null
            callStateManager.updateState("IDLE")
            isEnding = false
        }
    }

    private fun saveHistoryIfNeeded(finalStatus: String) {
        if (!isCaller) return
        if (activeCallerId.isEmpty() || activeReceiverId.isEmpty()) return

        val history = CallHistoryModel(
            callerId = activeCallerId,
            receiverId = activeReceiverId,
            duration = _callTime.value.toLong(),
            status = finalStatus
        )
        viewModelScope.launch {
            try {
                signalingClient.saveCallHistory(history)
            } catch (e: Exception) {
                Log.e("CALL", "Failed to save history", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("MYTAG", "callvm cleared")
    }
}

sealed class UiEvent {
    object EndCall : UiEvent()
}