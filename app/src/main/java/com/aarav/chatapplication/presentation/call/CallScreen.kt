package com.aarav.chatapplication.presentation.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aarav.chatapplication.R
import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.utils.formatTime
import kotlinx.coroutines.delay
import org.webrtc.SurfaceViewRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    callId: String,
    callerId: String,
    receiverId: String,
    callerName: String,
    isCaller: Boolean,
    onCallEnd: () -> Unit,
    viewModel: CallViewModel
) {

    val context = LocalContext.current

    val localView = remember {
        SurfaceViewRenderer(context)
    }

    val remoteView = remember {
        SurfaceViewRenderer(context)
    }

    val state by viewModel.callState.collectAsState()

    val callEnded by viewModel.callEnded.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()

    var isSpeakerOn by remember { mutableStateOf(true) }

    var videoReady by remember { mutableStateOf(false) }

    val videoAlpha by animateFloatAsState(
        targetValue = if (videoReady) 1f else 0f,
        label = "videoAlpha",
        animationSpec = tween(durationMillis = 700),
    )

    val time by viewModel.callTime.collectAsState()


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

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        audioManager.requestAudioFocus(focusRequest!!)
    } else {
        audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.AUDIOFOCUS_GAIN
        )
    }

    LaunchedEffect(callId) {

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        audioManager.isMicrophoneMute = false

        if (isCaller) {
            viewModel.startCall(
                CallModel(
                    callId = callId,
                    callerId = callerId,
                    receiverId = receiverId,
                    callerName = callerName
                )
            )
        } else {
            viewModel.receiveCall(callId)
        }
    }

    LaunchedEffect(Unit) {
        val eglContext = viewModel.getEglContext()

        remoteView.init(eglContext, null)
        remoteView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        remoteView.setZOrderOnTop(true)
        remoteView.setZOrderMediaOverlay(true)
        remoteView.setEnableHardwareScaler(true)
        remoteView.setZOrderMediaOverlay(false)

        localView.init(eglContext, null)
        localView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        localView.setZOrderMediaOverlay(true)
        localView.setEnableHardwareScaler(true)
        localView.setZOrderMediaOverlay(true)

        remoteView.setMirror(false)
        localView.setMirror(true)
    }

    LaunchedEffect(Unit) {
        viewModel.localVideoTrack.collect { track ->
            track?.let {
                it.setEnabled(true)
                it.addSink(localView)
            }
        }
    }
//    LaunchedEffect(state) {
//
//    }

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
        viewModel.remoteVideoTrack.collect { track ->


            if (track == null) {
                remoteView.clearImage()
                return@collect
            }

            videoReady = true

            Log.d("CALL", "remote $track")
            track.setEnabled(true)
            track.addSink(remoteView)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            remoteView.release()
            localView.release()
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

                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = false
                audioManager.isMicrophoneMute = false

                delay(1000)

                onCallEnd()
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize()
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {



            AndroidView(
                factory = { remoteView },
                modifier = Modifier.fillMaxSize()
                    .alpha(videoAlpha)
            )

            if (!videoReady) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(callerName, color = Color.White, fontSize = 22.sp)
                }
            }

            AndroidView(
                factory = { localView },
                modifier = Modifier
                    .padding(top = 78.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .size(120.dp)
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 16.dp)
            )

//        Column() {
//            Text(
//                text = when (state) {
//                    "CALLING" -> "Calling..."
//                    "RECEIVING" -> "Receiving Call..."
//                    "CONNECTING" -> "Connecting..."
//                    "CONNECTED" -> "Connected"
//                    "DISCONNECTED" -> "Disconnected"
//                    "FAILED" -> "Failed"
//                    "CLOSED" -> "Call Ended"
//                    "ENDED" -> "Call Ended"
//                    else -> "Initializing..."
//                }
//            )
//
//            Spacer(modifier = Modifier.height(20.dp))
//
//            if (!callEnded && state == "CONNECTED") {
//                Text("Time: ${time}s")
//            }
//        }


//        Row(
//            horizontalArrangement = Arrangement.SpaceEvenly,
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Button(onClick = {
//                viewModel.toggleMute()
//            }) {
//                Text(if (isMuted) "Unmute" else "Mute")
//            }
//
//            Button(onClick = {
//                isSpeakerOn = !isSpeakerOn
//                audioManager.isSpeakerphoneOn = isSpeakerOn
//            }) {
//                Text(if (isSpeakerOn) "Speaker ON" else "Speaker OFF")
//            }
//
//            Button(onClick = {
//                viewModel.endCall(callId)
//            }) {
//                Text("End Call")
//            }
//        }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Transparent) // optional blur feel
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
//
//                Surface(
//                    modifier = Modifier
//                        .align(Alignment.CenterStart)
//                        .clip(CircleShape)
//                        .size(32.dp)
//                        .clickable {
//
//                        }
//                        .background(
//                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
//                        )
//
//                ) {
//                    Icon(
//                        painter = painterResource(R.drawable.arrow_back),
//                        contentDescription = "Back",
//                        tint = Color.White,
//                        modifier = Modifier.size(24.dp)
//                    )
//                }
//
//                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.align(Alignment.Center)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (state) {
                            "CALLING" -> "Calling..."
                            "RECEIVING" -> "Receiving Call..."
                            "CONNECTING" -> "Connecting..."
                            "CONNECTED" -> "Connected"
                            "DISCONNECTED" -> "Disconnected"
                            "FAILED" -> "Failed"
                            "CLOSED", "ENDED" -> "Call Ended"
                            "BUSY" -> "User is Busy"
                            "REJECTED" -> "Call Declined"
                            "MISSED" -> "Missed Call"
                            "IDLE" -> if (callEnded) "Call Ended" else "Initializing..."
                            else -> "Initializing..."
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    if (!callEnded && state == "CONNECTED") {
                        Text(
                            text = formatTime(time),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            CallActionToolbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 54.dp),
                isMicEnabled = !isMuted,
                isSpeakerOn = isSpeakerOn,
                onMicClick = { viewModel.toggleMute() },
                onSpeakerClick = {
                    isSpeakerOn = !isSpeakerOn
                    audioManager.isSpeakerphoneOn = isSpeakerOn
                },
                onEndCallClick = {
                    viewModel.endCall(callId)
                },
                toggleCamera = {
                    viewModel.toggleCamera()
                }
            )

            CallActionToolbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 54.dp),
                isMicEnabled = !isMuted,
                isSpeakerOn = isSpeakerOn,
                onMicClick = { viewModel.toggleMute() },
                onSpeakerClick = {
                    isSpeakerOn = !isSpeakerOn
                    audioManager.isSpeakerphoneOn = isSpeakerOn
                },
                onEndCallClick = {
                    viewModel.endCall(callId)
                },
                toggleCamera = {
                    viewModel.toggleCamera()
                }
            )

        }
    }
}

@Preview(showBackground = true)
@Composable
fun CallActionToolbar(
    modifier: Modifier = Modifier,
    isMicEnabled: Boolean,
    isSpeakerOn: Boolean,
    onMicClick: () -> Unit,
    onSpeakerClick: () -> Unit,
    onEndCallClick: () -> Unit,
    toggleCamera: () -> Unit
) {

    val enabledContainer = MaterialTheme.colorScheme.surface
    val disabledContainer = MaterialTheme.colorScheme.surfaceDim
    val contentColor = MaterialTheme.colorScheme.onSurface
    val disabledContent = MaterialTheme.colorScheme.onSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .padding(horizontal = 24.dp)
//            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        IconButton(
            onClick = toggleCamera,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = enabledContainer,
                contentColor = contentColor
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.flip_camera),
                contentDescription = "Switch Camera"
            )
        }

        IconButton(
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (isMicEnabled) enabledContainer else disabledContainer,
                contentColor = contentColor
            ),
            onClick = onMicClick,
        ) {
            Icon(
                painter = painterResource(if (isMicEnabled) R.drawable.microphone_on else R.drawable.microphone_off),
                contentDescription = "mic toggle",
                modifier = Modifier.size(24.dp)
            )
        }
        IconButton(
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (isSpeakerOn) enabledContainer else disabledContainer,
                contentColor = contentColor
            ),
            onClick = onSpeakerClick,
        ) {
            Icon(
                painter = painterResource(if (isSpeakerOn) R.drawable.speaker_on else R.drawable.speaker_off),
                contentDescription = "speaker toggle",
                modifier = Modifier.size(24.dp)
            )
        }

        VerticalDivider(
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.height(32.dp)
        )

        IconButton(
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            onClick = onEndCallClick,
            // modifier = Modifier.size(24.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.end_call),
                contentDescription = "End Call",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun IncomingCallBanner(
    callerName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    val vibrator = context.getSystemService(Vibrator::class.java)

    val ringtone = remember {
        RingtoneManager.getRingtone(
            context,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        )
    }

    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager


    LaunchedEffect(Unit) {

        audioManager.mode = AudioManager.MODE_RINGTONE

        ringtone.play()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 1000),
                    0
                )
            )
        } else {
            vibrator?.vibrate(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose {

            audioManager.mode = AudioManager.MODE_NORMAL
            vibrator?.cancel()
            if (ringtone.isPlaying) ringtone.stop()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = callerName.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = callerName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Incoming call",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFE53935)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFE53935))
                ) {
                    Text("Decline")
                }

                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White
                    )
                ) {
                    Text("Accept")
                }
            }
        }
    }
}
