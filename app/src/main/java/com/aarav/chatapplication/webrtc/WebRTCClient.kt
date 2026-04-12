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

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    @Volatile
    private var isClosed = false

    private val _iceCandidateFlow = MutableSharedFlow<IceCandidate>(
        replay = 10
    )
    val iceCandidateFlow = _iceCandidateFlow.asSharedFlow()


    private val _connectionState = MutableStateFlow("NEW")
    val connectionState = _connectionState.asStateFlow()

    private var localAudioTrack: AudioTrack? = null

    fun init() {

        if (peerConnection != null) return

        isClosed = false
        _connectionState.value = "NEW"

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )

        peerConnectionFactory = PeerConnectionFactory.builder()
            .createPeerConnectionFactory()

        val iceServer = PeerConnection.IceServer
            .builder("stun:stun.l.google.com:19302")
            .createIceServer()

        peerConnection = peerConnectionFactory?.createPeerConnection(
            listOf(iceServer),
            peerConnectionObserver
        )

        val factory = peerConnectionFactory ?: return
        val pc = peerConnection ?: return
        val audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("audioTrack", audioSource)
        pc.addTrack(localAudioTrack)
    }

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {

        }


        override fun onIceConnectionReceivingChange(p0: Boolean) {}

        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

        override fun onIceCandidate(candidate: IceCandidate?) {
            if (isClosed) return
            candidate?.let {
                _iceCandidateFlow.tryEmit(it)
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            Log.i("CALL", "onConnectionChange: " + newState?.name)

            if (!isClosed) {
                _connectionState.value = newState?.name ?: "UNKNOWN"
            }
        }

        override fun onTrack(transceiver: RtpTransceiver?) {

            if (!isClosed) {
                val track = transceiver?.receiver?.track()
                if (track is AudioTrack) {
                    track.setEnabled(true)
                }
            }
        }

        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {}

        override fun onAddStream(p0: MediaStream?) {}

        override fun onRemoveStream(p0: MediaStream?) {}

        override fun onDataChannel(p0: DataChannel?) {}

        override fun onRenegotiationNeeded() {}

    }

    fun toggleMute(isMuted: Boolean) {
        Log.d("CALL", "MUTE: $isMuted")
        localAudioTrack?.setEnabled(!isMuted)
    }

    fun createOffer(onOfferCreated: (SessionDescription) -> Unit) {

        if (isClosed) return
        val pc = peerConnection ?: return


        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                if (isClosed) return
                pc.setLocalDescription(this, sdp)
                onOfferCreated(sdp)
            }

            override fun onSetSuccess() {}

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}

        }, MediaConstraints())
    }

    fun createAnswer(onAnswerCreated: (SessionDescription) -> Unit) {

        if (isClosed) return
        val pc = peerConnection ?: return


        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                if (isClosed) return
                pc.setLocalDescription(this, sdp)
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
        if (isClosed) return
        val pc = peerConnection ?: return

        val session = SessionDescription(
            SessionDescription.Type.OFFER,
            offer
        )

        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}

            override fun onSetSuccess() {
                createAnswer(onAnswerCreated)
            }

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}

        }, session)
    }

    fun onAnswerReceived(answer: String) {
        if (isClosed) return
        val pc = peerConnection ?: return

        val session = SessionDescription(
            SessionDescription.Type.ANSWER,
            answer
        )

        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}

            override fun onSetSuccess() {}

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}

        }, session)
    }

    fun addIceCandidate(candidate: IceCandidateModel) {
        if (isClosed) return
        val pc = peerConnection ?: return

        val ice = IceCandidate(
            candidate.sdpMid,
            candidate.sdpMLineIndex,
            candidate.sdp
        )

        pc.addIceCandidate(ice)
    }

    fun closeConnection() {
        if (isClosed) return
        isClosed = true

        try {
            peerConnection?.close()
            peerConnection?.dispose()
        } catch (e: Exception) {
            Log.e("CALL", "Error closing peer connection", e)
        }
        peerConnection = null

        try {
            peerConnectionFactory?.dispose()
        } catch (e: Exception) {
            Log.e("CALL", "Error disposing factory", e)
        }
        peerConnectionFactory = null
    }


}