package com.aarav.chatapplication.webrtc

import android.util.Log
import com.aarav.chatapplication.data.model.IceCandidateModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import javax.inject.Inject

class WebRTCClient
@Inject constructor(
    @ApplicationContext val context: android.content.Context
) {

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection

    private val _iceCandidateFlow = MutableSharedFlow<IceCandidate>(
        replay = 10
    )
    val iceCandidateFlow = _iceCandidateFlow.asSharedFlow()


    private val _connectionState = MutableStateFlow("NEW")
    val connectionState = _connectionState.asStateFlow()

    fun init() {

        if (::peerConnection.isInitialized) return

        // initialize WebRTC globally
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )

        // create factory
        peerConnectionFactory = PeerConnectionFactory.builder()
            .createPeerConnectionFactory()

        // create ice server (STUN)
        val iceServer = PeerConnection.IceServer
            .builder("stun:stun.l.google.com:19302")
            .createIceServer()

        // create peer connection
        peerConnection = peerConnectionFactory.createPeerConnection(
            listOf(iceServer),
            peerConnectionObserver
        )!!

        // create audioTrack from audioSource
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        val audioTrack = peerConnectionFactory.createAudioTrack("audioTrack", audioSource)

        // attach audiotrack before offer/answer
        peerConnection.addTrack(audioTrack)

    }

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {4

        }


        override fun onIceConnectionReceivingChange(p0: Boolean) {}

        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let {
                _iceCandidateFlow.tryEmit(it)
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            Log.i("CALL", "onConnectionChange: " + newState?.name)

            _connectionState.value = newState?.name ?: "UNKNOWN"
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            val track = transceiver?.receiver?.track()

            if (track is AudioTrack) {
                track.setEnabled(true)
            }
        }

        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {}

        override fun onAddStream(p0: MediaStream?) {}

        override fun onRemoveStream(p0: MediaStream?) {}

        override fun onDataChannel(p0: DataChannel?) {}

        override fun onRenegotiationNeeded() {}

    }

    fun createOffer(onOfferCreated: (SessionDescription) -> Unit) {

        peerConnection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection.setLocalDescription(this, sdp)
                onOfferCreated(sdp)
            }

            override fun onSetSuccess() {}

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}

        }, MediaConstraints())
    }

    fun createAnswer(onAnswerCreated: (SessionDescription) -> Unit) {
        peerConnection.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection.setLocalDescription(this, sdp)
                onAnswerCreated(sdp)
            }

            override fun onSetSuccess() {}

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}

        }, MediaConstraints())
    }

    fun onRemoteOfferReceived(
        offer: String,
        onAnswerCreated: (SessionDescription) -> Unit
    ) {
        val session = SessionDescription(
            SessionDescription.Type.OFFER,
            offer
        )

        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}

            override fun onSetSuccess() {
                createAnswer(onAnswerCreated)
            }

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}

        }, session)
    }

    fun onAnswerReceived(answer: String) {
        val session = SessionDescription(
            SessionDescription.Type.ANSWER,
            answer
        )

        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}

            override fun onSetSuccess() {}

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}

        }, session)
    }

    fun addIceCandidate(candidate: IceCandidateModel) {
        val ice = IceCandidate(
            candidate.sdpMid,
            candidate.sdpMLineIndex,
            candidate.sdp
        )

        peerConnection.addIceCandidate(ice)
    }


}