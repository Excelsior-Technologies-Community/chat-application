package com.aarav.chatapplication

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.domain.model.User
import com.aarav.chatapplication.domain.repository.UserRepository
import com.aarav.chatapplication.notification.MessageNotificationManager
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
    val callStateManager: CallStateManager,
    private val notificationManager: MessageNotificationManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    private val _incomingCall = MutableStateFlow<CallModel?>(null)
    val incomingCall = _incomingCall.asStateFlow()

    private val _currentUserName = MutableStateFlow("You")
    val currentUserName = _currentUserName.asStateFlow()

    fun listenForIncomingCalls(userId: String) {
        com.aarav.chatapplication.notification.ChatNotificationService.start(context, userId)
        
        viewModelScope.launch {
            userRepository.findUserByUserId(userId).collect {
                _currentUserName.value = it.name ?: "You"
            }
        }

        viewModelScope.launch {
            signalingClient.listenForIncomingCalls(userId)
                .collect { call ->

                    if (call.ended) {
                        _incomingCall.value = null
                        return@collect
                    }

                    val isMyActiveCall = call.callId == callStateManager.activeCallId

                    if (call.participants.contains(userId) && !isMyActiveCall) {

                        val isBusy =
                            callStateManager.callState.value != "IDLE" &&
                                    call.callId != callStateManager.activeCallId

                        if (isBusy) {
                            signalingClient.setBusy(call.callId)
                        } else {
                            if (_incomingCall.value?.callId != call.callId) {
                                Log.d("CALL", "New incoming call detected: ${call.callId}")
                                _incomingCall.value = call
                            }
                        }

                    } else {
                        _incomingCall.value = null
                    }
                }
        }
    }

    fun clearIncomingCall() {
        _incomingCall.value = null
    }

    fun stopNotificationListening() {
        com.aarav.chatapplication.notification.ChatNotificationService.stop(context)
    }
}