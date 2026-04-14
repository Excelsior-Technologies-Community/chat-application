package com.aarav.chatapplication.presentation.call

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.data.model.IceCandidateModel
import com.aarav.chatapplication.domain.repository.AuthRepository
import com.aarav.chatapplication.domain.repository.UserRepository
import com.aarav.chatapplication.webrtc.SignalingClient
import com.aarav.chatapplication.webrtc.WebRTCClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val signalingClient: SignalingClient,
    private val webRTCClient: WebRTCClient,
    private val userRepository: UserRepository
) : ViewModel() {

    private var isCaller = false
    private var callerName = ""
    private var isOfferHandled = false
    private var isAnswerHandled = false
    private var timerStarted = false

    private var activeCallId: String? = null

    @Volatile
    private var isEnding = false

    private val _callState = MutableStateFlow("IDLE")
    val callState = _callState.asStateFlow()

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

    init {
        connectionJob = viewModelScope.launch {
            webRTCClient.connectionState.collect { state ->
                if (!isEnding) {
                    Log.d("CONNECTION", "STATE: $state")
                    when (state) {
                        "CONNECTED" -> {
                            Log.d("CONNECTION", "connected: $state")
                            _callState.value = "CONNECTED"
                            startTimer()
                        }

                        "DISCONNECTED" -> _callState.value = "DISCONNECTED"
                        "FAILED" -> _callState.value = "FAILED"
                        "NEW" -> {
                            // ignore "NEW" state from WebRTC, if we don't, it instantly maps to IDLE and
                            // overwrites the "CALLING" or "RECEIVING" UI text
                        }
                        else -> {
                            _callState.value = "CONNECTING"
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
        isOfferHandled = false
        isAnswerHandled = false
        isEnding = false
        _isMuted.value = false
        timerStarted = false
        _callTime.value = 0
        // cancel and cleanly reset timer before initialization
        timerJob?.cancel()
        _callEnded.value = false
        _callState.value = "CALLING"

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
    }

    fun receiveCall(callId: String) {
        isCaller = false
        activeCallId = callId
        isOfferHandled = false
        isAnswerHandled = false
        isEnding = false
        timerStarted = false
        _callTime.value = 0
        _isMuted.value = false
        timerJob?.cancel()
        _callEnded.value = false
        _callState.value = "RECEIVING"

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
                        finishCall(call.callId)
                        return@collect
                    }

                    if (!isCaller && call?.offer != null && !isOfferHandled) {
                        isOfferHandled = true
                        _callState.value = "CONNECTING"

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
                        _callState.value = "CONNECTING"
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
        isEnding = true

        viewModelScope.launch {
            try {
                signalingClient.endCall(callId)
                webRTCClient.closeConnection()

                _callState.value = "ENDED"
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
            _callState.value = "IDLE"
            isEnding = false
        }
    }

    private fun finishCall(callId: String) {
        if (isEnding) return
        isEnding = true
        _callState.value = "ENDED"
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

            delay(1000)

            signalingClient.cleanupCallData(callId)
            activeCallId = null
            _callState.value = "IDLE"
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

    override fun onCleared() {
        super.onCleared()

        Log.d("MYTAG", "callvm cleared")
    }
}

sealed class UiEvent {
    object EndCall : UiEvent()
}