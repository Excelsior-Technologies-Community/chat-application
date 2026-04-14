package com.aarav.chatapplication.presentation.call

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.data.model.CallHistoryModel
import com.aarav.chatapplication.data.model.IceCandidateModel
import com.aarav.chatapplication.domain.repository.UserRepository
import com.aarav.chatapplication.webrtc.SignalingClient
import com.aarav.chatapplication.webrtc.CallStateManager
import com.aarav.chatapplication.webrtc.WebRTCClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val signalingClient: SignalingClient,
    private val webRTCClient: WebRTCClient,
    private val userRepository: UserRepository,
    private val callStateManager: CallStateManager
) : ViewModel() {

    private var isCaller = false
    private var callerName = ""
    private var isOfferHandled = false
    private var isAnswerHandled = false
    private var timerStarted = false

    private var activeCallId: String? = null
    private var activeCallerId: String = ""
    private var activeReceiverId: String = ""

    @Volatile
    private var isEnding = false

    val callState = callStateManager.callState

    private val _callEnded = MutableStateFlow(false)
    val callEnded = _callEnded.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()

    private val _callTime = MutableStateFlow(0)
    val callTime = _callTime.asStateFlow()

    // using Channel instead of MutableSharedFlow to ensure one-time UI navigation events
    // are queued and never dropped if the composable isn't actively collecting
    private val _events = Channel<UiEvent>()
    val events = _events.receiveAsFlow()

    val remoteVideoTrack = webRTCClient.remoteVideoTrackFlow
    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack = _localVideoTrack.asStateFlow()

    private var connectionJob: Job? = null
    private var signalingJob: Job? = null
    private var iceOutgoingJob: Job? = null
    private var iceIncomingJob: Job? = null
    private var timerJob: Job? = null
    private var isAnswered = false
    private var timeoutJob: Job? = null

    init {
        connectionJob = viewModelScope.launch {
            webRTCClient.connectionState.collect { state ->
                if (!isEnding) {
                    Log.d("CONNECTION", "STATE: $state")
                    when (state) {
                        "CONNECTED" -> {
                            Log.d("CONNECTION", "connected: $state")
                            callStateManager.updateState("CONNECTED")
                            startTimer()
                        }

                        "DISCONNECTED" -> callStateManager.updateState("DISCONNECTED")
                        "FAILED" -> callStateManager.updateState("FAILED")
                        "NEW" -> {
                            // ignore "NEW" state from WebRTC, if we don't, it instantly maps to IDLE and
                            // overwrites the "CALLING" or "RECEIVING" UI text
                        }

                        else -> {
                            callStateManager.updateState("CONNECTING")
                        }
                    }
                }
            }
        }
    }

    fun getEglContext(): EglBase.Context {
        return webRTCClient.getEglContext()
    }

    fun startCall(call: CallModel) {
        isCaller = true
        activeCallId = call.callId
        callStateManager.activeCallId = call.callId
        activeCallerId = call.callerId
        activeReceiverId = call.receiverId
        isOfferHandled = false
        isAnswerHandled = false
        isEnding = false
        _isMuted.value = false
        timerStarted = false
        _callTime.value = 0
        // cancel and cleanly reset timer before initialization
        timerJob?.cancel()
        _callEnded.value = false
        callStateManager.updateState("CALLING")

        viewModelScope.launch {
            webRTCClient.init()
            _localVideoTrack.value = webRTCClient.localVideoTrack
            signalingClient.createCall(call)

            webRTCClient.createOffer { sdp ->
                viewModelScope.launch {
                    if (!isEnding) {
                        signalingClient.sendOffer(call.callId, sdp.description)
                    }
                }
            }

            startObservers(call.callId)
        }

        timeoutJob?.cancel()

        timeoutJob = viewModelScope.launch {
            delay(30_000)

            if (!isAnswered && !isEnding) {
                Log.d("CALL", "Timeout reached, ending call")
                callStateManager.updateState("MISSED")
                activeCallId?.let { endCall(it) }
            }
        }
    }

    fun receiveCall(callId: String) {
        isCaller = false
        activeCallId = callId
        callStateManager.activeCallId = callId
        isOfferHandled = false
        isAnswerHandled = false
        isEnding = false
        timerStarted = false
        _callTime.value = 0
        _isMuted.value = false
        timerJob?.cancel()
        _callEnded.value = false
        callStateManager.updateState("RECEIVING")

        viewModelScope.launch {
            webRTCClient.init()
            _localVideoTrack.value = webRTCClient.localVideoTrack
            startObservers(callId)
        }
    }

    fun getLocalVideoTrack(): VideoTrack? {
        return webRTCClient.localVideoTrack
    }

    fun toggleCamera() {
        webRTCClient.switchCamera()
    }

    private fun startObservers(callId: String) {

        //connectionJob?.cancel()
        signalingJob?.cancel()
        iceOutgoingJob?.cancel()
        iceIncomingJob?.cancel()

        signalingJob = viewModelScope.launch {

            signalingClient.listenForCall(callId)
                .collect { call ->
                    if (isEnding) return@collect

                    if (call?.ended == true) {
                        if (call.isBusy) {
                            finishCall(call.callId, "BUSY")
                        } else if (!isAnswered && isCaller) {
                            finishCall(call.callId, "REJECTED")
                        } else {
                            finishCall(call.callId, "ENDED")
                        }
                        return@collect
                    }

                    if (!isCaller && call?.offer != null && !isOfferHandled) {
                        isOfferHandled = true
                        callStateManager.updateState("CONNECTING")

                        webRTCClient.onRemoteOfferReceived(call.offer) { answer ->
                            viewModelScope.launch {
                                if (!isEnding) {
                                    signalingClient.sendAnswer(callId, answer.description)
                                }
                            }
                        }
                    }

                    if (isCaller && call?.answer != null && !isAnswerHandled) {
                        isAnswerHandled = true
                        isAnswered = true
                        timeoutJob?.cancel()
                        callStateManager.updateState("CONNECTING")
                        webRTCClient.onAnswerReceived(call.answer)
                    }
                }
        }

        iceOutgoingJob = viewModelScope.launch {
            webRTCClient.iceCandidateFlow.collect { candidate ->
                if (!isEnding) {
                    try {
                        signalingClient.sendICECandidate(
                            callId,
                            IceCandidateModel(
                                sdp = candidate.sdp,
                                sdpMid = candidate.sdpMid,
                                sdpMLineIndex = candidate.sdpMLineIndex
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("CALL", "ICE send failed", e)
                    }
                }
            }
        }

        iceIncomingJob = viewModelScope.launch {
            signalingClient.listenForCandidate(callId)
                .collect { candidate ->
                    if (!isEnding) {
                        webRTCClient.addIceCandidate(candidate)
                    }
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

                callStateManager.updateState(if (currentStatus in listOf("MISSED", "BUSY", "REJECTED")) currentStatus else "ENDED")
                _callEnded.value = true

            } catch (e: Exception) {
                Log.e("CALL", "Error ending call", e)
            }

            // using trySend() instead of send(), if the user clicks decline from the home banner
            // without ever entering the CallScreen, there are no active collectors observing events
            // send() would suspend the coroutine indefinitely waiting for one, permanently freezing the logic below it
            _events.trySend(UiEvent.EndCall)
            _localVideoTrack.value = null
            _callEnded.value = true

//            connectionJob?.cancel()
//            connectionJob = null

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

            _localVideoTrack.value = null
            _callEnded.value = true

//        connectionJob?.cancel()
//        connectionJob = null

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

    fun toggleMute() {
        val newState = !_isMuted.value
        _isMuted.value = newState
        webRTCClient.toggleMute(newState)
    }

    fun getUserInfo(userId: String) {
        viewModelScope.launch {
            userRepository.findUserByUserId(userId).collect {
                callerName = it.name ?: "Test"
            }
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