package com.aarav.chatapplication.presentation.call

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    LaunchedEffect(callEnded) {
        if (callEnded) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL

            onCallEnd()
        }
    }

    LaunchedEffect(callId) {

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {
            viewModel.endCall(callId)
        }) {
            Text("End Call")
        }
    }
}