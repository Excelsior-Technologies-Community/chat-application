package com.aarav.chatapplication.presentation.call

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.data.model.IceCandidateModel
import com.aarav.chatapplication.webrtc.SignalingClient
import com.aarav.chatapplication.webrtc.WebRTCClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val signalingClient: SignalingClient,
    private val webRTCClient: WebRTCClient
) : ViewModel() {

    private var isCaller = false
    private var isOfferHandled = false
    private var isAnswerHandled = false
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

    fun startCall(call: CallModel) {
        isCaller = true
        activeCallId = call.callId
        isOfferHandled = false
        isAnswerHandled = false
        isEnding = false
        _callState.value = "CALLING"

        viewModelScope.launch {
            webRTCClient.init()
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
        _callState.value = "RECEIVING"

        viewModelScope.launch {
            webRTCClient.init()
            startObservers(callId)
        }
    }

    private fun startObservers(callId: String) {

        viewModelScope.launch {
            signalingClient.listenForCall(callId)
                .collect { call ->
                    if (isEnding) return@collect

                    if (call == null || call.ended) {
                        finishCall()
                        return@collect
                    }

                    if (!isCaller && call.offer != null && !isOfferHandled) {
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

                    if (isCaller && call.answer != null && !isAnswerHandled) {
                        isAnswerHandled = true
                        _callState.value = "CONNECTING"
                        webRTCClient.onAnswerReceived(call.answer)
                    }
                }
        }

        viewModelScope.launch {
            webRTCClient.connectionState.collect { state ->
                if (!isEnding) {
                    when (state) {
                        "CONNECTED" -> {
                            _callState.value = "CONNECTED"
                            startTimer()
                        }
                        "DISCONNECTED" -> _callState.value = "DISCONNECTED"
                        "FAILED" -> _callState.value = "FAILED"
                    }
                }
            }
        }

        viewModelScope.launch {
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

        viewModelScope.launch {
            signalingClient.listenForCandidate(callId)
                .collect { candidate ->
                    if (!isEnding) {
                        webRTCClient.addIceCandidate(candidate)
                    }
                }
        }
    }

    private fun startTimer() {
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
            } catch (e: Exception) {
                Log.e("CALL", "Error ending call", e)
            }
            _callState.value = "ENDED"
            _callEnded.value = true
        }
    }

    private fun finishCall() {
        if (isEnding) return
        isEnding = true
        _callState.value = "ENDED"
        _callEnded.value = true
    }

    fun toggleMute() {
        val newState = !_isMuted.value
        _isMuted.value = newState
        webRTCClient.toggleMute(newState)
    }

    override fun onCleared() {
        super.onCleared()
        webRTCClient.closeConnection()
        activeCallId?.let { signalingClient.cleanupCallData(it) }
    }
}