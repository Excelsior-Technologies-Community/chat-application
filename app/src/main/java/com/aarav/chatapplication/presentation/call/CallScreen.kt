package com.aarav.chatapplication.presentation.call

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    LaunchedEffect(callEnded) {
        if (callEnded) {
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL

            onCallEnd()
        }
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
                    receiverId = receiverId
                )
            )
        } else {
            viewModel.receiveCall(callId)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
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
                "CLOSED" -> "Call Ended"
                "ENDED" -> "Call Ended"
                else -> "Initializing..."
            }
        )


        Text("Time: ${time}s")

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                viewModel.toggleMute()
            }) {
                Text(if (isMuted) "Unmute" else "Mute")
            }

            Button(onClick = {
                isSpeakerOn = !isSpeakerOn
                audioManager.isSpeakerphoneOn = isSpeakerOn
            }) {
                Text(if (isSpeakerOn) "Speaker ON" else "Speaker OFF")
            }

            Button(onClick = {
                viewModel.endCall(callId)
            }) {
                Text("End Call")
            }
        }


    }
}