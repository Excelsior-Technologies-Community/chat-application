package com.aarav.chatapplication.webrtc

import android.util.Log
import com.aarav.chatapplication.data.model.IceCandidateModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack
import javax.inject.Inject

class WebRTCClient
@Inject constructor(
    @ApplicationContext val context: android.content.Context
) {

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnections = mutableMapOf<String, PeerConnection>()

    private val _iceCandidateFlow = MutableSharedFlow<Pair<IceCandidate, String>>(
        replay = 10
    )
    val iceCandidateFlow = _iceCandidateFlow.asSharedFlow()

    private val _allTracks =
        MutableStateFlow<Map<String, VideoTrack>>(emptyMap())

    val allTracks = _allTracks.asStateFlow()

    private val _connectionState = MutableStateFlow("NEW")
    val connectionState = _connectionState.asStateFlow()

    private var localAudioTrack: AudioTrack? = null
    var localVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var isFrontCamera = true
    private lateinit var eglBase: EglBase
    private val _eglContext = MutableStateFlow<EglBase.Context?>(null)
    val eglContext = _eglContext.asStateFlow()


    fun init() {

        if (peerConnectionFactory != null) return


        _connectionState.value = "NEW"


        Log.d("WEBRTC", "Initializing WebRTC (ONCE)")


        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )


        eglBase = EglBase.create()
        _eglContext.value = eglBase.eglBaseContext


        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    eglBase.eglBaseContext,
                    true,
                    true
                )
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        val factory = peerConnectionFactory ?: return

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }

        val audioSource = factory.createAudioSource(constraints)
        localAudioTrack = factory.createAudioTrack("audioTrack", audioSource)

    }

    fun createPeerConnection(userId: String) {


        val factory = peerConnectionFactory ?: return

        if (peerConnections.containsKey(userId)) return

        if (localAudioTrack == null || localVideoTrack == null) {
            Log.e("CALL", "Tracks not ready — skipping PC creation for $userId")
            return
        }


        val iceServers = listOf(
            // STUN
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                .createIceServer(),
            // TURN
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer()
        )

        val pc = factory.createPeerConnection(
            iceServers,
            createObserver(userId)
        ) ?: return

        val streamIds = listOf("ARDAMS")

        localAudioTrack?.let { pc.addTrack(it, streamIds) }
        localVideoTrack?.let { pc.addTrack(it, streamIds) }


        peerConnections[userId] = pc
        Log.d("CALL", "Creating PC for $userId")

    }

    private fun createObserver(userId: String) = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let {
                _iceCandidateFlow.tryEmit(it to userId)
            }
        }

        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {

        }

        override fun onAddStream(p0: MediaStream?) {
        }

        override fun onRemoveStream(p0: MediaStream?) {
        }

        override fun onDataChannel(p0: DataChannel?) {
        }

        override fun onRenegotiationNeeded() {
        }

        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
        }

        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            Log.i("CALL", "[$userId] $newState")
            if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                _connectionState.value = "CONNECTED"
            }
        }

        override fun onIceConnectionReceivingChange(p0: Boolean) {
        }

        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
        }

        override fun onTrack(transceiver: RtpTransceiver?) {

            val track = transceiver?.receiver?.track()
            if (track is AudioTrack) {
                track.setEnabled(true)
            }

            if (track is VideoTrack) {
                Log.d("CALL", "Remote video track received")
                //remoteVideoTrack = track
                val userId = userId

                _allTracks.value =
                    _allTracks.value.toMutableMap().apply {
                        put(userId, track)
                    }
            }
        }
    }

    fun startLocalVideo(): VideoTrack? {
        val peerConnectionFactory = peerConnectionFactory ?: return null

        if (videoCapturer == null) {
            videoCapturer = createCameraCapture()
        }

        val surfaceTextureHelper = SurfaceTextureHelper.create(
            "CaptureThread",
            eglBase.eglBaseContext
        )

        val videoSource = peerConnectionFactory.createVideoSource(false)

        videoCapturer?.initialize(
            surfaceTextureHelper,
            context,
            videoSource.capturerObserver
        )

        videoCapturer?.startCapture(720, 1280, 30)

        Log.d("CALL", "startCapture called")

        localVideoTrack = peerConnectionFactory.createVideoTrack("videoTrack", videoSource)
        localVideoTrack?.let {
            _allTracks.value = _allTracks.value.toMutableMap().apply {
                put("LOCAL", it)
            }
        }

        return localVideoTrack
    }

    fun createCameraCapture(): CameraVideoCapturer? {
        val enumerator = Camera1Enumerator(true)

        for (device in enumerator.deviceNames) {
            if (isFrontCamera && enumerator.isFrontFacing(device)) {
                return enumerator.createCapturer(device, null)
            } else if (!isFrontCamera && enumerator.isBackFacing(device)) {
                return enumerator.createCapturer(device, null)
            }
        }

        return null
    }

    fun switchCamera() {
        val capturer = videoCapturer

        if (capturer is CameraVideoCapturer) {
            capturer.switchCamera(null)
            isFrontCamera = !isFrontCamera
        }
    }


    fun toggleMute(isMuted: Boolean) {
        Log.d("CALL", "MUTE: $isMuted")
        localAudioTrack?.setEnabled(!isMuted)
    }

    fun createOffer(userId: String, onOfferCreated: (SessionDescription) -> Unit) {

        if (!peerConnections.containsKey(userId)) {
            createPeerConnection(userId)
        }

        val pc = peerConnections[userId] ?: return

        Log.d("CALL", "create offer for $userId")

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(this, sdp)
                onOfferCreated(sdp)
            }

            override fun onSetSuccess() {}

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}

        }, MediaConstraints())
    }

    fun createAnswer(userId: String, onAnswerCreated: (SessionDescription) -> Unit) {

        if (!peerConnections.containsKey(userId)) {
            createPeerConnection(userId)
        }

        val pc = peerConnections[userId] ?: return

        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(this, sdp)
                onAnswerCreated(sdp)
            }

            override fun onSetSuccess() {}

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}

        }, MediaConstraints())
    }

    fun onRemoteOfferReceived(
        userId: String,
        offer: String,
        onAnswerCreated: (SessionDescription) -> Unit
    ) {
        if (!peerConnections.containsKey(userId)) {
            createPeerConnection(userId)
        }

        val pc = peerConnections[userId] ?: return

        val session = SessionDescription(
            SessionDescription.Type.OFFER,
            offer
        )

        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}

            override fun onSetSuccess() {
                createAnswer(userId, onAnswerCreated)
            }

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}

        }, session)
    }

    fun onAnswerReceived(userId: String, answer: String) {
        if (!peerConnections.containsKey(userId)) {
            createPeerConnection(userId)
        }

        val pc = peerConnections[userId] ?: return

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

    fun addIceCandidate(userId: String, candidate: IceCandidateModel) {
        if (!peerConnections.containsKey(userId)) {
            createPeerConnection(userId)
        }

        val pc = peerConnections[userId] ?: return

        val ice = IceCandidate(
            candidate.sdpMid,
            candidate.sdpMLineIndex,
            candidate.sdp
        )


        pc.addIceCandidate(ice)

    }

    fun closeConnection() {

        try {
            // stop camera
            videoCapturer?.stopCapture()

//            videoCapturer?.dispose()
//            videoCapturer = null

            // dispose local tracks
//            localVideoTrack?.dispose()
//            localVideoTrack = null

//            localAudioTrack?.dispose()
//            localAudioTrack = null

            peerConnections.values.forEach {
                it.close()
                it.dispose()
            }

            peerConnections.clear()
            _connectionState.value = "IDLE"
            _allTracks.value = _allTracks.value.filterKeys { it == "LOCAL" }
        } catch (e: Exception) {
            Log.e("CALL", "Error closing peer connection", e)
        }
//
//        try {
//            peerConnectionFactory?.dispose()
//        } catch (e: Exception) {
//            Log.e("CALL", "Error disposing factory", e)
//        }
//        peerConnectionFactory = null
    }


}