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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aarav.chatapplication.R
import com.aarav.chatapplication.presentation.components.AddParticipantsSheet
import com.aarav.chatapplication.presentation.components.CreateChatModalSheet
import com.aarav.chatapplication.presentation.components.CustomBottomSheet
import com.aarav.chatapplication.ui.theme.hankenGrotesk
import com.aarav.chatapplication.utils.formatTime
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCallScreen(
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

    val state by viewModel.callState.collectAsState()

    val tracks by viewModel.tracks.collectAsState()

    val availableUsers by viewModel.availableUsers.collectAsState()

    val selectedUsers = remember { mutableStateListOf<String>() }

    val callEnded by viewModel.callEnded.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()

    var isSpeakerOn by remember { mutableStateOf(true) }

    val time by viewModel.callTime.collectAsState()


    val isVideoEnabled by viewModel.isVideoEnabled.collectAsState()
    val mediaStates by viewModel.mediaStates.collectAsState()

    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

//    LaunchedEffect(Unit) {
//        val eglContext = viewModel.getEglContext()
//
//        remoteView.init(eglContext, null)
//        remoteView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
//        remoteView.setZOrderOnTop(true)
//        remoteView.setZOrderMediaOverlay(true)
//        remoteView.setEnableHardwareScaler(true)
//        remoteView.setZOrderMediaOverlay(false)
//
//        localView.init(eglContext, null)
//        localView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
//        localView.setZOrderMediaOverlay(true)
//        localView.setEnableHardwareScaler(true)
//        localView.setZOrderMediaOverlay(true)
//
//        remoteView.setMirror(false)
//        localView.setMirror(true)
//    }

//    LaunchedEffect(Unit) {
//        viewModel.localVideoTrack.collect { track ->
//            track?.let {
//                it.setEnabled(true)
//                it.addSink(localView)
//            }
//        }
//    }
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

//    LaunchedEffect(Unit) {
//        viewModel.remoteVideoTrack.collect { track ->
//
//
//            if (track == null) {
//                remoteView.clearImage()
//                return@collect
//            }
//
//            videoReady = true
//
//            Log.d("CALL", "remote $track")
//            track.setEnabled(true)
//            track.addSink(remoteView)
//        }
//    }

//    DisposableEffect(Unit) {
//        onDispose {
//            remoteView.release()
//            localView.release()
//        }
//    }

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

                delay(1500)

                onCallEnd()
            }
        }
    }


    if(showSheet) {
        CustomBottomSheet(
            sheetState = sheetState,
            onDismiss = {
                showSheet = false
            },
            title = "Add Participant"
        ) {

            AddParticipantsSheet(
                userList = availableUsers,
                onDismiss = { showSheet = false },
                onAddClick = { selectedIds ->
                    viewModel.addParticipants(selectedIds)
                    showSheet = false
                }
            )
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
//            val myUserId = if (isCaller) callerId else receiverId


            if (eglBaseContext != null) {
                key(tracks.size) {
                    SmartVideoGrid(
                        tracks,
                        isVideoEnabled,
                        myUserId,
                        context,
                        eglBaseContext!!,
                        mediaStates
                    )
                }
            }

//            AndroidView(
//                factory = { remoteView },
//                modifier = Modifier.fillMaxSize()
//                    .alpha(videoAlpha)
//            )
//
//            if (!videoReady) {
//                Box(
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .background(Color.Black),
//                    contentAlignment = Alignment.Center
//                ) {
//                    Text(callerName, color = Color.White, fontSize = 22.sp)
//                }
//            }
//
//            AndroidView(
//                factory = { localView },
//                modifier = Modifier
//                    .padding(top = 78.dp)
//                    .clip(RoundedCornerShape(16.dp))
//                    .size(120.dp)
//                    .align(Alignment.TopEnd)
//                    .padding(horizontal = 16.dp)
//            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Transparent) // optional blur feel
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
                            "CALLING" -> "Calling..."
                            "RECEIVING" -> "Receiving GroupCall..."
                            "CONNECTING" -> "Connecting..."
                            "CONNECTED" -> "Connected"
                            "DISCONNECTED" -> "Disconnected"
                            "FAILED" -> "Failed"
                            "CLOSED", "ENDED" -> "GroupCall Ended"
                            "BUSY" -> "User is Busy"
                            "REJECTED" -> "GroupCall Declined"
                            "MISSED" -> "Missed GroupCall"
                            "IDLE" -> if (callEnded) "GroupCall Ended" else "Initializing..."
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

                IconButton(
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.7f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    onClick = {
                        viewModel.onAddParticipantClicked()
                        showSheet = true
                    },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add_user),
                        contentDescription = "Add Participant",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            CallActionToolbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 54.dp),
                isGroupCall = true,
                isCaller = isCaller,
                isMicEnabled = !isMuted,
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
                leaveCall = {
                    viewModel.leaveCall(callId)
                },
                toggleVideo = {
                    viewModel.toggleVideo()
                },
                toggleCamera = {
                    viewModel.toggleCamera()
                }
            )

//            CallActionToolbar(
//                modifier = Modifier
//                    .align(Alignment.BottomCenter)
//                    .padding(bottom = 54.dp),
//                isMicEnabled = !isMuted,
//                isSpeakerOn = isSpeakerOn,
//                onMicClick = { viewModel.toggleMute() },
//                onSpeakerClick = {
//                    isSpeakerOn = !isSpeakerOn
//                    audioManager.isSpeakerphoneOn = isSpeakerOn
//                },
//                onEndCallClick = {
//                    viewModel.endCall(callId)
//                },
//                toggleCamera = {
//                    viewModel.toggleCamera()
//                }
//            )

        }
    }
}

@Composable
fun CallActionToolbar(
    modifier: Modifier = Modifier,
    isCaller: Boolean,
    isGroupCall: Boolean,
    isMicEnabled: Boolean,
    isSpeakerOn: Boolean,
    isVideoEnabled: Boolean,
    isVideoCall: Boolean,
    onMicClick: () -> Unit,
    onSpeakerClick: () -> Unit,
    onEndCallClick: () -> Unit,
    leaveCall: () -> Unit,
    toggleVideo: () -> Unit,
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
        if (isVideoCall) {
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
        }


        if (isVideoCall) {
            IconButton(
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isVideoEnabled) enabledContainer else disabledContainer,
                    contentColor = contentColor
                ),
                onClick = toggleVideo,
            ) {
                Icon(
                    painter = painterResource(if (isVideoEnabled) R.drawable.camera_on else R.drawable.camera_off),
                    contentDescription = "video toggle",
                    modifier = Modifier.size(24.dp)
                )
            }
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
            onClick = {
                if (isGroupCall && !isCaller) {
                    leaveCall()
                } else {
                    onEndCallClick()
                }
            },
            // modifier = Modifier.size(24.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.end_call),
                contentDescription = "End GroupCall",
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

@Composable
fun SmartVideoGrid(
    tracks: Map<String, VideoTrack>,
    isLocalVideoEnabled: Boolean,
    myUserId: String,
    context: Context,
    eglBaseContext: EglBase.Context,
    mediaStates: Map<String, com.aarav.chatapplication.data.model.MediaState>
) {

    val users = tracks.entries.toList().sortedBy { it.key }
    val count = users.size
    val columns = kotlin.math.ceil(kotlin.math.sqrt(count.toDouble())).toInt()
    val rows = kotlin.math.ceil(count / columns.toDouble()).toInt()

    when (count) {

        0 -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Waiting for users...")
            }
        }

        1 -> {
            VideoItem(
                users[0].value,
                isLocalVideoEnabled,
                users[0].key,
                myUserId,
                context,
                eglBaseContext,
                mediaStates[users[0].key],
                Modifier.fillMaxSize()
            )
        }

        2 -> {
            Column(Modifier.fillMaxSize()) {
                users.forEach {
                    VideoItem(
                        it.value,
                        isLocalVideoEnabled,
                        it.key,
                        myUserId,
                        context,
                        eglBaseContext,
                        mediaStates[it.key],
                        Modifier.weight(1f)
                    )
                }
            }
        }

        else -> {
            Column(Modifier.fillMaxSize()) {

                var index = 0

                repeat(rows) { rowIndex ->

                    val remaining = count - index
                    val itemsInThisRow = if (rowIndex == rows - 1) {
                        remaining
                    } else {
                        columns
                    }

                    Row(
                        modifier = Modifier.weight(1f)
                    ) {

                        repeat(itemsInThisRow) {

                            val user = users[index]

                            VideoItem(
                                track = user.value,
                                isLocalVideoEnabled = isLocalVideoEnabled,
                                userId = user.key,
                                myUserId = myUserId,
                                context = context,
                                eglBaseContext = eglBaseContext,
                                mediaState = mediaStates[user.key],
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )

                            index++
                        }
                    }
                }
            }
        }
    }


}

@Composable
fun VideoGrid(
    tracks: Map<String, VideoTrack>,
    isLocalVideoEnabled: Boolean,
    myUserId: String,
    context: Context,
    eglBaseContext: EglBase.Context
) {
    val users = tracks.entries.toList()

    Log.d("VIDEO", "size: ${users.size}")

    when (users.size) {
        0 -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Waiting for users...")
            }
        }

        1 -> {
            VideoItem(
                users[0].value,
                isLocalVideoEnabled,
                users[0].key,
                myUserId,
                context,
                eglBaseContext,
                null,
                Modifier.fillMaxSize()
            )
        }

        2 -> {
            Column(Modifier.fillMaxSize()) {
                users.forEach {
                    VideoItem(
                        it.value,
                        isLocalVideoEnabled,
                        it.key,
                        myUserId,
                        context,
                        eglBaseContext,
                        null,
                        Modifier.weight(1f)
                    )
                }
            }
        }

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize()
            ) {
                items(users) {
                    VideoItem(
                        it.value,
                        isLocalVideoEnabled,
                        it.key,
                        myUserId,
                        context,
                        eglBaseContext,
                        null,
                        Modifier.aspectRatio(1f)
                    )
                }
            }
        }
    }
}


@Composable
fun VideoItem(
    track: VideoTrack,
    isLocalVideoEnabled: Boolean,
    userId: String,
    myUserId: String,
    context: Context,
    eglBaseContext: EglBase.Context,
    mediaState: com.aarav.chatapplication.data.model.MediaState?,
    modifier: Modifier = Modifier
) {


    // val isLocal = userId == myUserId
    val isLocal = userId == myUserId || userId == "LOCAL"

    val view = remember(eglBaseContext) {
        SurfaceViewRenderer(context).apply {
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 16f * context.resources.displayMetrics.density)
                }
            }
        }
    }


    DisposableEffect(eglBaseContext) {

        view.init(eglBaseContext, null)
        view.setMirror(isLocal)
        view.setZOrderMediaOverlay(true)
        view.setEnableHardwareScaler(true)

        view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)

        onDispose {
            track.removeSink(view)
            view.release()
        }

    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        val showVideo = if (isLocal) isLocalVideoEnabled else (mediaState?.videoEnabled ?: true)
        val isMuted = mediaState?.muted ?: false

        if (!showVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(R.drawable.camera_off),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
//                    Spacer(Modifier.height(8.dp))
//                    Text(
//                        if (isLocal) "You" else userId,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant,
//                        fontSize = 12.sp
//                    )
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    view
                },
                update = {
                    try {
                        Log.d("VIDEO", "Rendering track for $userId")
                        track.setEnabled(true)
                        track.addSink(view)
                    } catch (e: Exception) {
                        Log.e("VIDEO", "Track already disposed for $userId")
                    }
                }
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isLocal) "You" else userId,
                color = Color.White,
                fontSize = 12.sp
            )
            if (isMuted) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.microphone_off),
                    contentDescription = "Muted",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

//@Composable
//fun LocalPreview(
//    localTrack: VideoTrack
//) {
//    Box(
//        modifier = Modifier
//            .size(120.dp)
//            .clip(RoundedCornerShape(16.dp))
//    ) {
//        AndroidView(
//            factory = { context ->
//                SurfaceViewRenderer(context).apply {
//                    init(EglBase.create().eglBaseContext, null)
//                    setMirror(true)
//                    localTrack.addSink(this)
//                }
//            }
//        )
//    }
//}