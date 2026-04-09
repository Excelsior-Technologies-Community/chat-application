package com.aarav.chatapplication.presentation.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.data.model.IceCandidateModel
import com.aarav.chatapplication.webrtc.SignalingClient
import com.aarav.chatapplication.webrtc.WebRTCClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallViewModel
@Inject constructor(
    val signalingClient: SignalingClient,
    val webRTCClient: WebRTCClient
) : ViewModel() {

    private var isOfferHandled = false
    private var isAnswerHandled = false
    private var isCaller = false

    val connectionState = webRTCClient.connectionState


    fun startCall(call: CallModel) {
        isCaller = true
        isOfferHandled = false
        isAnswerHandled = false

        listenForUpdates(call.callId)
        observeIceOutgoing(call.callId)
        observeIceIncoming(call.callId)

        viewModelScope.launch {
            webRTCClient.init()

            signalingClient.createCall(call)

            webRTCClient.createOffer { sdp ->

                viewModelScope.launch {
                    signalingClient.sendOffer(call.callId, sdp.description)
                }
            }
        }
    }

    fun receiveCall(callId: String) {

        isCaller = false
        isOfferHandled = false
        isAnswerHandled = false

        observeIceOutgoing(callId)
        observeIceIncoming(callId)
        listenForUpdates(callId)

        viewModelScope.launch {
            webRTCClient.init()
        }
    }

    fun listenForUpdates(callId: String) {
        viewModelScope.launch {
            signalingClient.listenForCall(callId)
                .collect { call ->

                    // receiver
                    if (!isCaller && call.offer != null && !isOfferHandled) {
                        isOfferHandled = true

                        webRTCClient.onRemoteOfferReceived(call.offer) { answer ->

                            viewModelScope.launch {
                                signalingClient.sendAnswer(callId, answer.description)
                            }
                        }
                    }

                    // caller only
                    if (isCaller && call.answer != null && !isAnswerHandled) {
                        isAnswerHandled = true

                        webRTCClient.onAnswerReceived(call.answer)
                    }
                }
        }
    }

    fun observeIceOutgoing(callId: String) {
        viewModelScope.launch {
            webRTCClient.iceCandidateFlow.collect { candidate ->
                signalingClient.sendICECandidate(
                    callId,
                    IceCandidateModel(
                        sdp = candidate.sdp,
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex
                    )
                )
            }
        }
    }

    fun observeIceIncoming(callId: String) {
        viewModelScope.launch {
            signalingClient.listenForCandidate(callId)
                .collect { candidate ->
                    webRTCClient.addIceCandidate(candidate)
                }
        }
    }

}