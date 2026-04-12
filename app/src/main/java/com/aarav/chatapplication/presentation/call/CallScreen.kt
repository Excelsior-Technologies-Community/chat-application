package com.aarav.chatapplication.presentation.call

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aarav.chatapplication.R
import com.aarav.chatapplication.data.model.CallModel
import kotlinx.coroutines.delay

@Composable
fun CallScreen(
    callId: String,
    callerId: String,
    receiverId: String,
    isCaller: Boolean,
    onCallEnd: () -> Unit,
    viewModel: CallViewModel
) {

    val context = LocalContext.current

    val state by viewModel.callState.collectAsState()

    val callEnded by viewModel.callEnded.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()

    var isSpeakerOn by remember { mutableStateOf(true) }

    val time by viewModel.callTime.collectAsState()


    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

//    LaunchedEffect(callEnded) {
//        if (callEnded) {
//
//        }
//    }

    LaunchedEffect(callId) {

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        audioManager.isMicrophoneMute = false

        if (isCaller) {
            viewModel.startCall(
                CallModel(
                    callId = callId,
                    callerId = callerId,
                    receiverId = receiverId
                )
            )
        } else {
            viewModel.receiveCall(callId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is UiEvent.EndCall) {
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_NORMAL

                delay(800)

                onCallEnd()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        Column() {
            Text(
                text = when (state) {
                    "CALLING" -> "Calling..."
                    "RECEIVING" -> "Receiving Call..."
                    "CONNECTING" -> "Connecting..."
                    "CONNECTED" -> "Connected"
                    "DISCONNECTED" -> "Disconnected"
                    "FAILED" -> "Failed"
                    "CLOSED" -> "Call Ended"
                    "ENDED" -> "Call Ended"
                    else -> "Initializing..."
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            if(!callEnded) {
                Text("Time: ${time}s")
            }
        }


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

        CallActionToolbar(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 54.dp),
            isMicEnabled = !isMuted,
            isSpeakerOn = isSpeakerOn,
            onMicClick = { viewModel.toggleMute() },
            onSpeakerClick = {
                isSpeakerOn = !isSpeakerOn
                audioManager.isSpeakerphoneOn = isSpeakerOn
            }
        ) {
            viewModel.endCall(callId)
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
    onEndCallClick: () -> Unit
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
            color = MaterialTheme.colorScheme.outlineVariant,
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