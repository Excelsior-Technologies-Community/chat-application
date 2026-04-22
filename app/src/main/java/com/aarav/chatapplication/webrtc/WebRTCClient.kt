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
import org.webrtc.audio.JavaAudioDeviceModule
import javax.inject.Inject

class WebRTCClient
@Inject constructor(
    @ApplicationContext val context: android.content.Context
) {

    private val TAG = "CONNECTION"

    private val streamIds = listOf("ARDAMS")
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnections = mutableMapOf<String, PeerConnection>()

    private val connectedPeers = mutableSetOf<String>()

    private val _peerStates =
        MutableStateFlow<Map<String, PeerConnection.PeerConnectionState>>(emptyMap())

    val peerStates = _peerStates.asStateFlow()

    private val _iceCandidateFlow = MutableSharedFlow<Pair<IceCandidate, String>>(
        replay = 10
    )
    val iceCandidateFlow = _iceCandidateFlow.asSharedFlow()

    private val _allTracks =
        MutableStateFlow<Map<String, VideoTrack>>(emptyMap())

    val allTracks = _allTracks.asStateFlow()

    private val _connectionState = MutableStateFlow("NEW")
    val connectionState = _connectionState.asStateFlow()

    private var isMuted = false

    private var localAudioTrack: AudioTrack? = null
    var localVideoTrack: VideoTrack? = null
    private var localVideoSource: org.webrtc.VideoSource? = null
    private var localAudioSource: org.webrtc.AudioSource? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
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
    private val remoteAudioTracks = mutableMapOf<String, AudioTrack>()

    var onPeerConnected: ((String) -> Unit)? = null


    fun init() {

        if (peerConnectionFactory != null) return


        _connectionState.value = "NEW"

        Log.d(TAG, "Initializing WebRTC (Singleton)")


        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )


        eglBase = EglBase.create()
        _eglContext.value = eglBase.eglBaseContext


        audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    eglBase.eglBaseContext,
                    true,
                    true
                )
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        if (localAudioTrack == null) {
            createAudioTrack()
        }

    }

    fun createPeerConnection(userId: String) {
        val factory = peerConnectionFactory ?: run {
            Log.e(TAG, "createPeerConnection: factory is null")
            return
        }

        if (peerConnections.containsKey(userId)) {
            Log.d(TAG, "PC already exists for $userId: skipping")
            return
        }

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:relay1.expressturn.com:3478")
                .setUsername("efzP6H3J5P3S1ZC8")
                .setPassword("k4wTzZy6WzZx3n5F")
                .createIceServer()
        )

        val pc = factory.createPeerConnection(
            iceServers,
            createObserver(userId)
        ) ?: run {
            Log.e(TAG, "factory.createPeerConnection returned null for $userId")
            return
        }

        //createAudioTrack()

        localAudioTrack?.let { pc.addTrack(it, streamIds) }
        //localAudioTrack?.setEnabled(true)
        localVideoTrack?.let { pc.addTrack(it, streamIds) }

        peerConnections[userId] = pc
        remoteDescriptionSet.remove(userId)
        Log.d(TAG, "PeerConnection CREATED for $userId (total PCs: ${peerConnections.size})")
    }

    fun createAudioTrack() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }

        val factory = peerConnectionFactory ?: return

        localAudioSource = factory.createAudioSource(constraints)
        localAudioTrack = factory.createAudioTrack("audioTrack", localAudioSource)
        localAudioTrack?.setEnabled(true)
        configureAudioForCall()
    }

    private fun createObserver(userId: String) = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let {
                _iceCandidateFlow.tryEmit(it to userId)
            }
        }

        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {}
        override fun onAddStream(p0: MediaStream?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            Log.i("MESH", "[$userId] PeerConnection state → $newState")

            _peerStates.value = _peerStates.value.toMutableMap().apply {
                put(userId, newState)
            }

            when (newState) {
                PeerConnection.PeerConnectionState.CONNECTED -> {
                    if (connectedPeers.add(userId)) {
                        onPeerConnected?.invoke(userId)
                    }
                    _connectionState.value = "CONNECTED"
                    enableAllAudio()
                }

                PeerConnection.PeerConnectionState.CONNECTING -> _connectionState.value =
                    "CONNECTING"

                PeerConnection.PeerConnectionState.FAILED -> {
                    connectedPeers.remove(userId)
                    peerConnections.remove(userId)
                    onPeerConnected?.invoke(userId)
                    Log.e("MESH", "[$userId] PeerConnection FAILED — check ICE/TURN servers")
                    _connectionState.value = "FAILED"
                }

                PeerConnection.PeerConnectionState.DISCONNECTED -> {
                    connectedPeers.remove(userId)
                    peerConnections.remove(userId)
                    onPeerConnected?.invoke(userId)
                    _connectionState.value = "DISCONNECTED"
                }

                PeerConnection.PeerConnectionState.CLOSED -> {
                    if (!isClosingAllConnections) {
                        removePeerConnection(userId)
                        connectedPeers.remove(userId)
                    }
                    if (peerConnections.isEmpty()) {
                        _connectionState.value = "IDLE"
                    }
                }

                else -> Unit
            }
        }

        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

        override fun onTrack(transceiver: RtpTransceiver?) {
            val track = transceiver?.receiver?.track()
            Log.d("MESH", "[$userId] onTrack: ${track?.kind()} (id=${track?.id()})")
            if (track is AudioTrack) {
                track.setEnabled(true)
                track.setVolume(10.0)
                remoteAudioTracks[userId] = track
                Log.d(
                    "MESH",
                    "[$userId] ✓ Remote AUDIO track enabled (total audio: ${remoteAudioTracks.size})"
                )
            }
            if (track is VideoTrack) {
                _allTracks.value = _allTracks.value.toMutableMap().apply {
                    put(userId, track)
                }
                Log.d(
                    "MESH",
                    "[$userId] ✓ Remote VIDEO track added (total videos: ${_allTracks.value.size})"
                )
            }
        }
    }

    fun enableAllAudio() {
        localAudioTrack?.setEnabled(!isMuted)
        remoteAudioTracks.forEach { (uid, track) ->
            track.setEnabled(true)
            track.setVolume(10.0)
            Log.d("CALL", "Re-enabled remote audio for $uid")
        }
        refreshAudioRouting()
    }

    private fun refreshAudioRouting() {
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (audioManager.mode != AudioManager.MODE_IN_COMMUNICATION) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }
        audioManager.isMicrophoneMute = false
    }

    fun startLocalVideo(): VideoTrack? {

        val factory = peerConnectionFactory ?: return null

        if (localVideoTrack != null) {
            videoCapturer?.startCapture(720, 1280, 30)
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
            localVideoSource = factory.createVideoSource(false)
        }

        videoCapturer?.initialize(
            surfaceTextureHelper,
            context,
            localVideoSource?.capturerObserver
        )

        videoCapturer?.startCapture(720, 1280, 30)

        localVideoTrack = factory.createVideoTrack("videoTrack", localVideoSource)
        localVideoTrack?.setEnabled(true)

        _allTracks.value = _allTracks.value.toMutableMap().apply {
            put("LOCAL", localVideoTrack!!)
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

    fun stopLocalVideo() {
        try {
            videoCapturer?.stopCapture()
            Log.d(TAG, "Stop VideoCapture")

        } catch (e: Exception) {
            Log.e(TAG, "stopCapture error", e)
        }
//        videoCapturer?.dispose()
//        videoCapturer = null
    }

    fun toggleMute(isMuted: Boolean) {
        this.isMuted = isMuted
        Log.d("CALL", "MUTE: $isMuted")

        localAudioTrack?.setEnabled(!isMuted)
    }

    fun ensureAudioEnabled() {
        localAudioTrack?.setEnabled(true)
    }

    fun createOffer(userId: String, onOfferCreated: (SessionDescription) -> Unit) {

        if (!peerConnections.containsKey(userId)) {
            createPeerConnection(userId)
        }

        val pc = peerConnections[userId] ?: run {
            Log.e("MESH", "createOffer: No PC for $userId")
            return
        }

        Log.d("MESH", "createOffer for $userId")

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d("MESH", "Offer SDP created for $userId")
                pc.setLocalDescription(this, sdp)
                onOfferCreated(sdp)
            }

            override fun onSetSuccess() {
                Log.d("MESH", "Local description set for offer → $userId")
            }

            override fun onCreateFailure(p0: String?) {
                peerConnections.remove(userId)
                onPeerConnected?.invoke(userId)
                Log.e("MESH", "FAILED to create offer for $userId: $p0")
            }

            override fun onSetFailure(p0: String?) {
                Log.e("MESH", "FAILED to set local desc (offer) for $userId: $p0")
            }

        }, constraints)
    }

    fun createAnswer(userId: String, onAnswerCreated: (SessionDescription) -> Unit) {

        if (!peerConnections.containsKey(userId)) {
            createPeerConnection(userId)
        }

        val pc = peerConnections[userId] ?: run {
            Log.e("MESH", "createAnswer: No PC for $userId")
            return
        }

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.d("MESH", "Answer SDP created for $userId")
                pc.setLocalDescription(this, sdp)
                onAnswerCreated(sdp)
            }

            override fun onSetSuccess() {
                Log.d("MESH", "Local description set for answer → $userId")
            }

            override fun onCreateFailure(p0: String?) {
                Log.e("MESH", "FAILED to create answer for $userId: $p0")
            }

            override fun onSetFailure(p0: String?) {
                Log.e("MESH", "FAILED to set local desc (answer) for $userId: $p0")
            }

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

        val pc = peerConnections[userId] ?: run {
            Log.e("MESH", "onRemoteOfferReceived: No PC for $userId")
            return
        }

        Log.d("MESH", "Setting remote offer from $userId")

        val session = SessionDescription(
            SessionDescription.Type.OFFER,
            offer
        )

        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}

            override fun onSetSuccess() {
                Log.d("MESH", "Remote offer set SUCCESS for $userId → creating answer")
                remoteDescriptionSet.add(userId)
                applyBufferedIceCandidates(userId)
                createAnswer(userId, onAnswerCreated)
            }

            override fun onCreateFailure(p0: String?) {
                Log.e("MESH", "FAILED create (remote offer) for $userId: $p0")
            }

            override fun onSetFailure(p0: String?) {
                Log.e("MESH", "FAILED to set remote offer for $userId: $p0")
            }

        }, session)
    }

    fun onAnswerReceived(userId: String, answer: String) {
        if (!peerConnections.containsKey(userId)) {
            createPeerConnection(userId)
        }

        val pc = peerConnections[userId] ?: run {
            Log.e("MESH", "onAnswerReceived: No PC for $userId")
            return
        }

        Log.d("MESH", "Setting remote answer from $userId")

        val session = SessionDescription(
            SessionDescription.Type.ANSWER,
            answer
        )

        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}

            override fun onSetSuccess() {
                Log.d("MESH", "Remote answer set SUCCESS for $userId → connection should start")
                remoteDescriptionSet.add(userId)
                applyBufferedIceCandidates(userId)
            }

            override fun onCreateFailure(p0: String?) {
                Log.e("MESH", "FAILED create (remote answer) for $userId: $p0")
            }

            override fun onSetFailure(p0: String?) {
                Log.e("MESH", "FAILED to set remote answer for $userId: $p0")
            }

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

    fun enableVideo() {
        localVideoTrack?.setEnabled(true)
    }

    fun disableVideo() {
        localVideoTrack?.setEnabled(false)
    }

    fun closeConnection() {

        try {

            isClosingAllConnections = true
            peerConnections.values.toList().forEach {
                it.close()
                it.dispose()
            }

            peerConnections.clear()
            pendingIceCandidates.clear()
            remoteDescriptionSet.clear()
            connectedPeers.clear()
            _peerStates.value = emptyMap()
            remoteAudioTracks.clear()
            _connectionState.value = "NEW"
            _allTracks.value = _allTracks.value.filterKeys { it == "LOCAL" }
            restoreAudioAfterCall()
        } catch (e: Exception) {
            Log.e("CALL", "Error closing peer connection", e)
        } finally {

            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null

            localAudioTrack?.dispose()
            localAudioTrack = null

            localVideoTrack?.dispose()
            localVideoTrack = null

            localVideoSource?.dispose()
            localVideoSource = null

            localAudioSource?.dispose()
            localAudioSource = null

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

        _peerStates.value = _peerStates.value.toMutableMap().apply {
            remove(userId)
        }

        pendingIceCandidates.remove(userId)
        remoteDescriptionSet.remove(userId)
        remoteAudioTracks.remove(userId)
        _allTracks.value = _allTracks.value
            .filterKeys { it != userId }
            .toMap()
        //_allTracks.value = _allTracks.value.toMutableMap().apply { remove(userId) }
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
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
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
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager.mode = previousAudioMode
        audioManager.isSpeakerphoneOn = previousSpeakerphone
        audioManager.isMicrophoneMute = previousMicMute
        isAudioConfigured = false
    }
}