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

@Composable
fun CallScreen(
    callId: String,
    callerId: String,
    receiverId: String,
    isCaller: Boolean,
    viewModel: CallViewModel
) {

    val context = LocalContext.current

    val state by viewModel.connectionState.collectAsState()

    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    audioManager.isSpeakerphoneOn = true

    LaunchedEffect(Unit) {

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
            text = when {
                state == "NEW" && isCaller -> "Calling..."
                state == "NEW" && !isCaller -> "Receiving Call"
                state == "CONNECTED" -> "Connected"
                state == "DISCONNECTED" -> "Disconnected"
                state == "FAILED" -> "Failed"
                state == "CLOSED" -> "Closed"
                else -> "Connecting..."
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {

        }) {
            Text("End Call")
        }
    }
}