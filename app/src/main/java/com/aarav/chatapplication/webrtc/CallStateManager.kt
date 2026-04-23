package com.aarav.chatapplication.webrtc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallStateManager @Inject constructor() {
    private val _callState = MutableStateFlow("IDLE")
    val callState: StateFlow<String> = _callState.asStateFlow()

    var activeCallId: String? = null

    fun updateState(newState: String) {
        _callState.value = newState
        if (newState == "IDLE") {
            activeCallId = null
        }
    }
}