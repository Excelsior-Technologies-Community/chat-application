package com.aarav.chatapplication.webrtc

import android.util.Log
import android.view.SurfaceView
import com.aarav.chatapplication.data.model.IceCandidateModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
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
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack
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

    private val _remoteVideoTrackFlow = MutableSharedFlow<VideoTrack>(
        replay = 1
    )
    val remoteVideoTrackFlow = _remoteVideoTrackFlow.asSharedFlow()

    private val _connectionState = MutableStateFlow("NEW")
    val connectionState = _connectionState.asStateFlow()

    private var localAudioTrack: AudioTrack? = null
    var localVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private lateinit var eglBase: EglBase
    private var remoteVideoTrack: VideoTrack? = null


    fun init() {

        if (peerConnection != null) return

        isClosed = false
        _connectionState.value = "NEW"

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )


        eglBase = EglBase.create()


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

        peerConnection = peerConnectionFactory?.createPeerConnection(
            iceServers,
            peerConnectionObserver
        )

        startLocalVideo()


        peerConnection?.addTransceiver(
            org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(
                RtpTransceiver.RtpTransceiverDirection.SEND_RECV
            )
        )



        val factory = peerConnectionFactory ?: return
        val pc = peerConnection ?: return
        val audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("audioTrack", audioSource)

        pc.addTrack(localAudioTrack)
        localVideoTrack?.let {
            pc.addTrack(it)
        }

    }


    fun getEglContext() = eglBase.eglBaseContext

    fun startLocalVideo(): VideoTrack? {
        val peerConnection = peerConnection ?: return null
        val peerConnectionFactory = peerConnectionFactory ?: return null

        videoCapturer = createCameraCapture()

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

        return localVideoTrack
    }

    fun createCameraCapture(): VideoCapturer? {
        val enumerator = Camera1Enumerator(true)

        for (device in enumerator.deviceNames) {
            if(enumerator.isFrontFacing(device)) {
                return enumerator.createCapturer(device, null)
            }
        }

        return null
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

            if (!isClosed) {
                Log.i("CALL", "onConnectionChange: " + newState?.name)
                _connectionState.value = newState?.name ?: "UNKNOWN"
            }
        }

        override fun onTrack(transceiver: RtpTransceiver?) {

            if (!isClosed) {
                val track = transceiver?.receiver?.track()
                if (track is AudioTrack) {
                    track.setEnabled(true)
                }

                if (track is VideoTrack) {
                    Log.d("CALL", "Remote video track received")
                    remoteVideoTrack = track
                    _remoteVideoTrackFlow.tryEmit(track)
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
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null

            localVideoTrack?.dispose()
            localVideoTrack = null

            localAudioTrack?.dispose()
            localAudioTrack = null

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