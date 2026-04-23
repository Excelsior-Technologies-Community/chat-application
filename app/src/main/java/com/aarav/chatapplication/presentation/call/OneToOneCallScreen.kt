package com.aarav.chatapplication.presentation.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import com.aarav.chatapplication.presentation.components.CallInfoSheet
import com.aarav.chatapplication.presentation.components.CustomBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aarav.chatapplication.R
import com.aarav.chatapplication.utils.formatTime
import kotlinx.coroutines.delay
import org.webrtc.SurfaceViewRenderer
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.toArgb

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneToOneCallScreen(
    callId: String,
    myUserId: String,
    callerName: String,
    isCaller: Boolean,
    isVideoCall: Boolean,
    onCallEnd: () -> Unit,
    viewModel: CallViewModel
) {
    val context = LocalContext.current

    val eglBaseContext by viewModel.eglContext.collectAsState()

    val localView = remember {
        SurfaceViewRenderer(context).apply {
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 16f * context.resources.displayMetrics.density)
                }
            }
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                v.invalidateOutline()
            }
        }
    }

    val remoteView = remember {
        SurfaceViewRenderer(context).apply {
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 16f * context.resources.displayMetrics.density)
                }
            }
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                v.invalidateOutline()
            }
        }
    }

    val state by viewModel.callState.collectAsState()

    val tracks by viewModel.tracks.collectAsState()

    val callEnded by viewModel.callEnded.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()

    var isSpeakerOn by remember { mutableStateOf(true) }

    val time by viewModel.callTime.collectAsState()

    val isVideoEnabled by viewModel.isVideoEnabled.collectAsState()
    val mediaStates by viewModel.mediaStates.collectAsState()

    val activeParticipants by viewModel.activeParticipants.collectAsState()
    val userNames by viewModel.usersMapping.collectAsState()

    var showInfoSheet by remember { mutableStateOf(false) }
    val infoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val remoteUserId = activeParticipants.firstOrNull { it != myUserId }
        ?: tracks.keys.firstOrNull { it != "LOCAL" }
        ?: mediaStates.keys.firstOrNull { it != myUserId }

    val displayRemoteName = if (remoteUserId != null) {
        userNames[remoteUserId] ?: "Unknown"
    } else {
        if (isCaller) "Connecting..." else callerName
    }

    val isRemoteVideoEnabled = remoteUserId?.let { mediaStates[it]?.videoEnabled } ?: true
    val isRemoteMuted = remoteUserId?.let { mediaStates[it]?.muted } ?: false

    val isRemoteReady = tracks.any { it.key != "LOCAL" } && state == "CONNECTED"

    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val focusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener {}
            .build()
    } else null

    LaunchedEffect(callId) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = isSpeakerOn
        audioManager.isMicrophoneMute = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        if (!isCaller) {
            viewModel.receiveCall(callId, myUserId, isVideoCall)
        }
    }

    LaunchedEffect(state) {
        if (state == "CONNECTED") {
            viewModel.refreshAudio()
        }
    }

    LaunchedEffect(isSpeakerOn) {
        audioManager.isSpeakerphoneOn = isSpeakerOn
    }

    LaunchedEffect(state) {
        if (state == "BUSY") {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100)
                toneGen.startTone(ToneGenerator.TONE_SUP_BUSY, 2000)
                kotlinx.coroutines.delay(2000)
                toneGen.release()
            } catch (e: Exception) {
                Log.e("CALL", "Tone fail", e)
            }
        }
    }

    LaunchedEffect(Unit) {

        viewModel.events.collect { event ->
            if (event is UiEvent.EndCall) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioManager.abandonAudioFocusRequest(focusRequest!!)
                } else {
                    audioManager.abandonAudioFocus(null)
                }

                try {
                    localView.clearImage()
                    remoteView.clearImage()
                } catch (_: Exception) {
                }

                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = false
                audioManager.isMicrophoneMute = false

                delay(1500)

                onCallEnd()
            }
        }
    }

    LaunchedEffect(tracks) {
        if (isVideoCall && !callEnded) {

            val localTrack = tracks["LOCAL"]
            localTrack?.let {
                try {
                    localTrack.addSink(localView)
                } catch (_: Exception) {
                }
            }

            val remoteTrack = tracks.entries
                .firstOrNull { it.key != "LOCAL" }
                ?.value

            remoteTrack?.let {
                try {
                    it.addSink(remoteView)
                } catch (_: Exception) {
                }
            }
        }
    }

    LaunchedEffect(eglBaseContext) {
        if (eglBaseContext != null) {

            remoteView.init(eglBaseContext, null)
            remoteView.setMirror(false)
            remoteView.setEnableHardwareScaler(true)
            remoteView.setZOrderMediaOverlay(false)
            remoteView.setZOrderOnTop(false)

            localView.init(eglBaseContext, null)
            localView.setMirror(true)
            localView.setZOrderOnTop(true)
            localView.setZOrderMediaOverlay(true)
            localView.setEnableHardwareScaler(true)
        }
    }

    DisposableEffect(Unit) {

        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(focusRequest!!)
            } else {
                audioManager.abandonAudioFocus(null)
            }

            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            audioManager.isMicrophoneMute = false

            try {
                localView.clearImage()
                localView.release()
            } catch (_: Exception) {
            }

            try {
                remoteView.clearImage()
                remoteView.release()
            } catch (_: Exception) {
            }

        }
    }

    if (showInfoSheet) {
        CustomBottomSheet(
            sheetState = infoSheetState,
            onDismiss = { showInfoSheet = false },
            title = "Call Info"
        ) {
            CallInfoSheet(
                participants = userNames,
                activeParticipants = setOf(myUserId, remoteUserId ?: ""),
                onDismiss = { showInfoSheet = false }
            )
        }
    }

    Scaffold(
        containerColor = Color(0xFF121212),
        modifier = Modifier.fillMaxSize()
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {

            if (isVideoCall) {

                Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
                    AndroidView(
                        factory = { remoteView },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (!isRemoteVideoEnabled || !isRemoteReady) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF2C2C2C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        androidx.compose.foundation.shape.CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayRemoteName.take(1).uppercase(),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 32.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 140.dp, start = 16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayRemoteName,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                        if (isRemoteMuted) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                painter = painterResource(R.drawable.microphone_off),
                                contentDescription = "Muted",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (!isRemoteVideoEnabled || !isRemoteReady) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                painter = painterResource(R.drawable.camera_off),
                                contentDescription = "Camera Off",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (isVideoEnabled) {
                    AndroidView(
                        factory = { localView },
                        modifier = Modifier
                            .padding(top = 78.dp, end = 16.dp)
                            .size(120.dp, 160.dp)
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .padding(top = 78.dp, end = 16.dp)
                            .size(120.dp, 160.dp)
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF2C2C2C)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    androidx.compose.foundation.shape.CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Y",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 20.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF2C2C2C)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    androidx.compose.foundation.shape.CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayRemoteName.take(1).uppercase(),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 48.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(displayRemoteName, color = Color.White, fontSize = 24.sp)

                        if (isRemoteMuted) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.microphone_off),
                                    contentDescription = "Muted",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Muted", color = Color.White, fontSize = 14.sp)
                            }
                        }

                        if (!callEnded && state == "CONNECTED") {
                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = formatTime(time),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {

                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (state) {
                            "CALLING" -> "Calling $displayRemoteName..."
                            "RECEIVING" -> "Incoming call from $displayRemoteName..."
                            "CONNECTING" -> "Connecting to $displayRemoteName..."
                            "CONNECTED" -> displayRemoteName
                            "DISCONNECTED" -> "Disconnected"
                            "FAILED" -> "Failed"
                            "CLOSED", "ENDED" -> "Call Ended"
                            "BUSY" -> "$displayRemoteName is Busy"
                            "REJECTED" -> "Call Declined"
                            "MISSED" -> "Missed Call"
                            "IDLE" -> if (callEnded) "Call Ended" else "Initializing..."
                            else -> "Initializing..."
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    if (!callEnded && state == "CONNECTED" && isVideoCall) {
                        Text(
                            text = formatTime(time),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                IconButton(
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.7f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    onClick = {
                        showInfoSheet = true
                    },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.info),
                        contentDescription = "Call Info",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            CallActionToolbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 54.dp),
                isMicEnabled = !isMuted,
                isCaller = isCaller,
                isGroupCall = false,
                isSpeakerOn = isSpeakerOn,
                isVideoEnabled = isVideoEnabled,
                isVideoCall = isVideoCall,
                onMicClick = { viewModel.toggleMute() },
                onSpeakerClick = {
                    isSpeakerOn = !isSpeakerOn
                    audioManager.isSpeakerphoneOn = isSpeakerOn
                },
                onEndCallClick = {
                    viewModel.endCall(callId)
                },
                leaveCall = {},
                toggleVideo = {
                    viewModel.toggleVideo()
                },
                toggleCamera = {
                    viewModel.toggleCamera()
                }
            )

        }

    }

}