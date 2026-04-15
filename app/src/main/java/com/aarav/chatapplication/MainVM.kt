package com.aarav.chatapplication

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.domain.model.User
import com.aarav.chatapplication.domain.repository.UserRepository
import com.aarav.chatapplication.webrtc.CallStateManager
import com.aarav.chatapplication.webrtc.SignalingClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainVM
@Inject constructor(
    val signalingClient: SignalingClient,
    val userRepository: UserRepository,
    val callStateManager: CallStateManager
) : ViewModel() {
    private val _incomingCall = MutableStateFlow<CallModel?>(null)
    val incomingCall = _incomingCall.asStateFlow()
    private val _callEnded = MutableSharedFlow<String>()
    val callEnded = _callEnded.asSharedFlow()


    fun listenForIncomingCalls(userId: String) {
        viewModelScope.launch {
            signalingClient.listenForIncomingCalls(userId)
                .collect { call ->

                    if (call.ended) {
                        _incomingCall.value = null
                        return@collect
                    }

                    val myOffer = call.offers.get(userId)
                    val myAnswer = call.answers.get(userId)

                    if (myOffer != null && myAnswer == null) {

                        val isBusy =
                            callStateManager.callState.value != "IDLE" &&
                                    call.callId != callStateManager.activeCallId

                        if (isBusy) {
                            signalingClient.setBusy(call.callId)
                        } else {
                            _incomingCall.value = call
                        }

                    } else {
                        _incomingCall.value = null
                    }
                }
        }
    }

}