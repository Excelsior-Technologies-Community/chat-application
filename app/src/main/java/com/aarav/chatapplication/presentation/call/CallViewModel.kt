package com.aarav.chatapplication.presentation.call

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.data.model.IceCandidateModel
import com.aarav.chatapplication.webrtc.SignalingClient
import com.aarav.chatapplication.webrtc.WebRTCClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val signalingClient: SignalingClient,
    private val webRTCClient: WebRTCClient
) : ViewModel() {

    private var isCaller = false
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

    val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()

    val remoteVideoTrack = webRTCClient.remoteVideoTrackFlow
    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack = _localVideoTrack.asStateFlow()

    private var connectionJob: Job? = null
    private var signalingJob: Job? = null
    private var iceOutgoingJob: Job? = null
    private var iceIncomingJob: Job? = null

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
                        else -> _callState.value = "CONNECTING"
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
        _callEnded.value = false
//        _callState.value = "CALLING"

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
        _callEnded.value = false
        //_callState.value = "RECEIVING"

        viewModelScope.launch {
            webRTCClient.init()
            _localVideoTrack.value = webRTCClient.localVideoTrack
            startObservers(callId)
        }
    }

    fun getLocalVideoTrack(): VideoTrack? {
        return webRTCClient.localVideoTrack
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

                    if (call == null || call.ended) {
                        finishCall()
                        return@collect
                    }

                    if (!isCaller && call.offer != null && !isOfferHandled) {
                        isOfferHandled = true
                        //_callState.value = "CONNECTING"

                        webRTCClient.onRemoteOfferReceived(call.offer) { answer ->
                            viewModelScope.launch {
                                if (!isEnding) {
                                    signalingClient.sendAnswer(callId, answer.description)
                                }
                            }
                        }
                    }

                    if (isCaller && call.answer != null && !isAnswerHandled) {
                        isAnswerHandled = true
                        //_callState.value = "CONNECTING"
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
        viewModelScope.launch {
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
            } catch (e: Exception) {
                Log.e("CALL", "Error ending call", e)
            }
            _callState.value = "ENDED"
            _events.emit(UiEvent.EndCall)
            _callEnded.value = true
        }
    }

    private fun finishCall() {
        if (isEnding) return
        isEnding = true
        _callState.value = "ENDED"
        viewModelScope.launch {
            webRTCClient.closeConnection()
            _events.emit(UiEvent.EndCall)
        }
        _callEnded.value = true
    }

    fun toggleMute() {
        val newState = !_isMuted.value
        _isMuted.value = newState
        webRTCClient.toggleMute(newState)
    }

    override fun onCleared() {
        super.onCleared()

        connectionJob?.cancel()
        connectionJob = null

        signalingJob?.cancel()
        signalingJob = null

        iceOutgoingJob?.cancel()
        iceOutgoingJob = null

        iceIncomingJob?.cancel()
        iceIncomingJob = null

        activeCallId?.let {
            signalingClient.cleanupCallData(it)
            activeCallId = null
        }
    }
}

sealed class UiEvent {
    object EndCall : UiEvent()
}