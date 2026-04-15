package com.aarav.chatapplication.webrtc

import android.content.Context
import android.media.AudioManager
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
    private var localVideoSource: org.webrtc.VideoSource? = null
    private var localAudioSource: org.webrtc.AudioSource? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var isCapturing = false
    private var isFrontCamera = true
    private var isAudioConfigured = false
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var previousSpeakerphone = false
    private var previousMicMute = false
    private var isClosingAllConnections = false
    private lateinit var eglBase: EglBase
    private val _eglContext = MutableStateFlow<EglBase.Context?>(null)
    val eglContext = _eglContext.asStateFlow()
    private val pendingIceCandidates = mutableMapOf<String, MutableList<IceCandidate>>()
    private val remoteDescriptionSet = mutableSetOf<String>()


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

        localAudioSource = factory.createAudioSource(constraints)
        localAudioTrack = factory.createAudioTrack("audioTrack", localAudioSource)
        localAudioTrack?.setEnabled(true)
        configureAudioForCall()

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
        remoteDescriptionSet.remove(userId)
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
            when (newState) {
                PeerConnection.PeerConnectionState.CONNECTED -> _connectionState.value = "CONNECTED"
                PeerConnection.PeerConnectionState.CONNECTING -> _connectionState.value = "CONNECTING"
                PeerConnection.PeerConnectionState.FAILED -> _connectionState.value = "FAILED"
                PeerConnection.PeerConnectionState.DISCONNECTED -> _connectionState.value = "DISCONNECTED"
                PeerConnection.PeerConnectionState.CLOSED -> {
                    if (!isClosingAllConnections) {
                        removePeerConnection(userId)
                    }
                    if (peerConnections.isEmpty()) {
                        _connectionState.value = "IDLE"
                    }
                }
                else -> Unit
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
                _allTracks.value =
                    _allTracks.value.toMutableMap().apply {
                        put(userId, track)
                    }
            }
        }
    }

    fun startLocalVideo(): VideoTrack? {
        val peerConnectionFactory = peerConnectionFactory ?: return null

        if (localVideoTrack != null) {
            if (!isCapturing) {
                try {
                    videoCapturer?.startCapture(720, 1280, 30)
                    isCapturing = true
                } catch (e: Exception) {
                    Log.e("CALL", "Failed to restart local capture", e)
                }
            }
            localVideoTrack?.setEnabled(true)
            _allTracks.value = _allTracks.value.toMutableMap().apply { localVideoTrack?.let { put("LOCAL", it) } }
            configureAudioForCall()
            return localVideoTrack
        }

        if (videoCapturer == null) {
            videoCapturer = createCameraCapture()
        }

        if (surfaceTextureHelper == null) {
            surfaceTextureHelper = SurfaceTextureHelper.create(
                "CaptureThread",
                eglBase.eglBaseContext
            )
        }

        if (localVideoSource == null) {
            localVideoSource = peerConnectionFactory.createVideoSource(false)
        }

        videoCapturer?.initialize(
            surfaceTextureHelper,
            context,
            localVideoSource?.capturerObserver
        )

        videoCapturer?.startCapture(720, 1280, 30)
        isCapturing = true

        Log.d("CALL", "startCapture called")

        localVideoTrack = peerConnectionFactory.createVideoTrack("videoTrack", localVideoSource)
        localVideoTrack?.setEnabled(true)
        localVideoTrack?.let {
            _allTracks.value = _allTracks.value.toMutableMap().apply {
                put("LOCAL", it)
            }
        }
        configureAudioForCall()

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

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(this, sdp)
                onOfferCreated(sdp)
            }

            override fun onSetSuccess() {}

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}

        }, constraints)
    }

    fun createAnswer(userId: String, onAnswerCreated: (SessionDescription) -> Unit) {

        if (!peerConnections.containsKey(userId)) {
            createPeerConnection(userId)
        }

        val pc = peerConnections[userId] ?: return

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(this, sdp)
                onAnswerCreated(sdp)
            }

            override fun onSetSuccess() {}

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}

        }, constraints)
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
                remoteDescriptionSet.add(userId)
                applyBufferedIceCandidates(userId)
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

            override fun onSetSuccess() {
                remoteDescriptionSet.add(userId)
                applyBufferedIceCandidates(userId)
            }

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}

        }, session)
    }

    fun addIceCandidate(userId: String, candidate: IceCandidateModel) {
        if (!peerConnections.containsKey(userId)) {
            createPeerConnection(userId)
        }

        val pc = peerConnections[userId]

        val ice = IceCandidate(
            candidate.sdpMid,
            candidate.sdpMLineIndex,
            candidate.sdp
        )
        if (pc == null || !remoteDescriptionSet.contains(userId) || pc.remoteDescription == null) {
            pendingIceCandidates.getOrPut(userId) { mutableListOf() }.add(ice)
            Log.d("CALL", "Buffering ICE for $userId until remote SDP is set")
            return
        }
        pc.addIceCandidate(ice)

    }

    fun closeConnection() {

        try {
            if (isCapturing) {
                videoCapturer?.stopCapture()
                isCapturing = false
            }

            isClosingAllConnections = true
            peerConnections.values.toList().forEach {
                it.close()
                it.dispose()
            }
            isClosingAllConnections = false

            peerConnections.clear()
            pendingIceCandidates.clear()
            remoteDescriptionSet.clear()
            _connectionState.value = "NEW"
            _allTracks.value = _allTracks.value.filterKeys { it == "LOCAL" }
            restoreAudioAfterCall()
        } catch (e: Exception) {
            Log.e("CALL", "Error closing peer connection", e)
        } finally {
            isClosingAllConnections = false
        }
    }


    fun removePeerConnection(userId: String) {
        peerConnections.remove(userId)?.let {
            try {
                it.close()
                it.dispose()
            } catch (e: Exception) {
                Log.e("CALL", "Failed to remove PC for $userId", e)
            }
        }
        pendingIceCandidates.remove(userId)
        remoteDescriptionSet.remove(userId)
        _allTracks.value = _allTracks.value.toMutableMap().apply { remove(userId) }
    }

    private fun applyBufferedIceCandidates(userId: String) {
        val pc = peerConnections[userId] ?: return
        val buffered = pendingIceCandidates.remove(userId).orEmpty()
        buffered.forEach { pc.addIceCandidate(it) }
        if (buffered.isNotEmpty()) {
            Log.d("CALL", "Applied ${buffered.size} buffered ICE candidates for $userId")
        }
    }

    private fun configureAudioForCall() {
        if (isAudioConfigured) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        previousAudioMode = audioManager.mode
        previousSpeakerphone = audioManager.isSpeakerphoneOn
        previousMicMute = audioManager.isMicrophoneMute
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        audioManager.isMicrophoneMute = false
        isAudioConfigured = true
    }

    private fun restoreAudioAfterCall() {
        if (!isAudioConfigured) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager.mode = previousAudioMode
        audioManager.isSpeakerphoneOn = previousSpeakerphone
        audioManager.isMicrophoneMute = previousMicMute
        isAudioConfigured = false
    }
}