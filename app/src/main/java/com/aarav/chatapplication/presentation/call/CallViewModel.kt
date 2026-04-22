package com.aarav.chatapplication.presentation.call

import android.media.AudioManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.data.model.CallHistoryModel
import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.data.model.IceCandidateModel
import com.aarav.chatapplication.data.model.OfferModel
import com.aarav.chatapplication.data.model.MediaState
import com.aarav.chatapplication.domain.model.User
import com.aarav.chatapplication.domain.repository.ChatListRepository
import com.aarav.chatapplication.domain.repository.GroupChatRepository
import com.aarav.chatapplication.domain.repository.UserRepository
import com.aarav.chatapplication.presentation.model.DirectChatEntry
import com.aarav.chatapplication.presentation.model.GroupChatEntry
import com.aarav.chatapplication.webrtc.CallStateManager
import com.aarav.chatapplication.webrtc.SignalingClient
import com.aarav.chatapplication.webrtc.WebRTCClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack
import javax.inject.Inject
import kotlin.Boolean
import kotlin.Pair
import kotlin.String
import kotlin.collections.listOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.time.Duration.Companion.seconds

private const val TAG = "CONNECTION"

@HiltViewModel
class CallViewModel @Inject constructor(
    private val signalingClient: SignalingClient,
    private val webRTCClient: WebRTCClient,
    private val callStateManager: CallStateManager,
    private val userRepository: UserRepository
) : ViewModel() {

    private var isCaller = false

    private val handledOfferKeys = mutableSetOf<String>()
    private val handledAnswerKeys = mutableSetOf<String>()
    private val peerCreated = mutableSetOf<String>()

    val peerStates = webRTCClient.peerStates

    private val connectionQueue = ArrayDeque<String>()

    private val _availableUsers = MutableStateFlow<List<User>>(emptyList())
    val availableUsers = _availableUsers.asStateFlow()

    private val _usersMapping = MutableStateFlow<Map<String, String>>(emptyMap())
    val usersMapping = _usersMapping.asStateFlow()

    private val _activeParticipants = MutableStateFlow<Set<String>>(emptySet())
    val activeParticipants = _activeParticipants.asStateFlow()

    private var isProcessing = false

    private var timerStarted = false

    private var activeCallId: String? = null
    private var activeCallerId: String = ""
    private var activeReceiverId: String = ""

    private var myUserId: String = ""
    private var isAnswered = false

    @Volatile
    private var isEnding = false

    val callState = callStateManager.callState

    private val _callEnded = MutableStateFlow(false)
    val callEnded = _callEnded.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()

    private val _callTime = MutableStateFlow(0)
    val callTime = _callTime.asStateFlow()

    private val _events = Channel<UiEvent>()
    val events = _events.receiveAsFlow()

    val tracks = webRTCClient.allTracks
//    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
//    val localVideoTrack = _localVideoTrack.asStateFlow()
    val eglContext = webRTCClient.eglContext

    private var connectionJob: Job? = null
    private var signalingJob: Job? = null
    private var participantJob: Job? = null
    private var iceOutgoingJob: Job? = null
    private var iceIncomingJob: Job? = null
    private var timerJob: Job? = null
    private var timeoutJob: Job? = null


    private val _isVideoEnabled = MutableStateFlow(false)
    val isVideoEnabled = _isVideoEnabled.asStateFlow()

    private val _mediaStates = MutableStateFlow<Map<String, MediaState>>(emptyMap())
    val mediaStates = _mediaStates.asStateFlow()

    init {

        webRTCClient.onPeerConnected = { userId ->
            Log.d(TAG, "[$myUserId] Connected → $userId")

            isProcessing = false


            viewModelScope.launch {
                delay(300)
                processQueue()
            }
        }

        viewModelScope.launch {
            userRepository.getAllUsers().collect { users ->
                _usersMapping.value = users.associateBy({ it.uid ?: "" }, { it.name ?: "Unknown" })
            }
        }

        connectionJob = viewModelScope.launch {
            webRTCClient.peerStates.collect { peerStates ->

                if (isEnding) return@collect

                val states = peerStates.values

                val newState = when {

                    states.any { it == PeerConnection.PeerConnectionState.CONNECTED } -> {
                        "CONNECTED"
                    }

                    states.any { it == PeerConnection.PeerConnectionState.CONNECTING } -> {
                        "CONNECTING"
                    }

                    states.isNotEmpty() && states.all {
                        it == PeerConnection.PeerConnectionState.DISCONNECTED ||
                                it == PeerConnection.PeerConnectionState.FAILED
                    } -> {
                        "DISCONNECTED"
                    }

                    states.isEmpty() -> {
                        "IDLE"
                    }

                    else -> {
                        "DISCONNECTED"
                    }
                }

                val currentState = callStateManager.callState.value

                if (newState == "CONNECTED" && currentState != "CONNECTED") {
                    Log.d(TAG, "Call connected → starting timer")
                    startTimer()
                }

                if (currentState != newState) {
                    Log.d(TAG, "GLOBAL STATE → $newState | peers=$peerStates")
                    callStateManager.updateState(newState)
                }
            }
        }
    }

    fun startCall(call: CallModel, myUserId: String) {
        this.myUserId = myUserId
        isCaller = true
        resetState()

        activeCallId = call.callId
        callStateManager.activeCallId = call.callId
        activeCallerId = call.callerId
        activeReceiverId = call.participants.keys.firstOrNull { it != myUserId } ?: ""
        //activeReceiverId = call.participants.firstOrNull { it != myUserId } ?: ""
        _activeParticipants.update { setOf(myUserId) }
        Log.d(TAG, "[$myUserId] STARTING CALL ${call.callId} | participants=${call.participants}")

        callStateManager.updateState("CALLING")

        viewModelScope.launch {
            webRTCClient.init()

            if(call.videoCall) {
                webRTCClient.startLocalVideo()
                _isVideoEnabled.value = true
            }
            updateMyMediaState()

            //_localVideoTrack.value = webRTCClient.localVideoTrack

            signalingClient.createCall(call)

//            val others = call.participants.filter { it != myUserId }
//            others.forEach { userId -> enqueue(userId) }

//            val others = call.participants.keys.filter { it != myUserId }
//            Log.d(TAG, "[$myUserId] Will send offers to: $others")
//            others.forEach { enqueue(it) }

            startObservers(call.callId)
        }

        timeoutJob = viewModelScope.launch {
            delay(30_000)
            if (!isAnswered && !isEnding) {
                callStateManager.updateState("MISSED")
                activeCallId?.let { endCall(it) }
            }
        }
    }

    fun receiveCall(callId: String, myUserId: String, isVideoCall: Boolean) {
        this.myUserId = myUserId
        isCaller = false
        resetState()

        activeCallId = callId
        callStateManager.activeCallId = callId
        activeReceiverId = myUserId
        _activeParticipants.update { setOf(myUserId) }
        Log.d(TAG, "[$myUserId] RECEIVING CALL $callId")

        callStateManager.updateState("RECEIVING")

        viewModelScope.launch {
            webRTCClient.init()


            if(isVideoCall) {
                webRTCClient.startLocalVideo()
                _isVideoEnabled.value = true
            }
            updateMyMediaState()

            //_localVideoTrack.value = webRTCClient.localVideoTrack
            startObservers(callId)
        }
    }

    private fun resetState() {
        handledOfferKeys.clear()
        handledAnswerKeys.clear()
        peerCreated.clear()
        connectionQueue.clear()
        isProcessing = false
        isAnswered = false
        isEnding = false
        timerStarted = false
        _callTime.value = 0
        _isMuted.value = false
        _callEnded.value = false
        timerJob?.cancel()
        timeoutJob?.cancel()
    }

    private fun ensurePeerConnection(userId: String) {
        if (peerCreated.add(userId)) {
            Log.d(TAG, "[$myUserId] Creating PeerConnection for $userId")
            webRTCClient.createPeerConnection(userId)
        }
    }

    private fun enqueue(userId: String) {
        if (connectionQueue.contains(userId) || peerCreated.contains(userId)) return

        Log.d(TAG, "[$myUserId] Queue add: $userId")
        connectionQueue.add(userId)
        processQueue()
    }

    private fun processQueue() {
        if (isProcessing || connectionQueue.isEmpty()) return

        val next = connectionQueue.removeFirst()
        isProcessing = true

        Log.d(TAG, "[$myUserId] Processing: $next")

        val callId = activeCallId
        if (callId == null) {
            isProcessing = false
            return
        }

        sendOfferTo(callId, next)
    }

    private fun sendOfferTo(callId: String, userId: String) {
        ensurePeerConnection(userId)
        Log.d(TAG, "[$myUserId] Creating & sending OFFER for: $userId")
        webRTCClient.createOffer(userId) { sdp ->
            viewModelScope.launch {
                if (!isEnding) {
                    signalingClient.sendOffer(
                        callId, userId,
                        OfferModel(sdp.description, myUserId)
                    )
                    Log.d(TAG, "[$myUserId] OFFER sent to $userId (Firebase key: ${myUserId}_$userId)")
                }
            }
        }
    }

    private fun startObservers(callId: String) {
        signalingJob?.cancel()
        participantJob?.cancel()
        iceOutgoingJob?.cancel()
        iceIncomingJob?.cancel()

        signalingJob = viewModelScope.launch {
            signalingClient.listenForCall(callId).collect { call ->
                if (isEnding) return@collect
                if (call == null) {
                    Log.w(TAG, "[$myUserId] GroupCall data is null: call may have been deleted")
                    return@collect
                }

                if (activeCallerId.isEmpty()) activeCallerId = call.callerId

                if (call.ended) {
                    Log.d(TAG, "[$myUserId] GroupCall ended flag detected")
                    when {
                        call.isBusy -> finishCall(call.callId, "BUSY")
                        !isAnswered && isCaller -> finishCall(call.callId, "REJECTED")
                        else -> finishCall(call.callId, "ENDED")
                    }
                    return@collect
                }

                _mediaStates.value = call.mediaStates
//
//                // MESH: discover new participants and decide who offers
//                call.participants.forEach { peerId ->
//                    if (peerId == myUserId) return@forEach
//                    if (peerCreated.contains(peerId)) return@forEach
//
//                    val iAmInitiator = isCaller || myUserId < peerId
//                    if (iAmInitiator) {
//                        Log.d(TAG, "[$myUserId] Mesh: I should offer to $peerId (isCaller=$isCaller, myId<peerId=${myUserId < peerId})")
//                        enqueue(peerId)
//                    } else {
//                        Log.d(TAG, "[$myUserId] Mesh: waiting for $peerId to offer to me")
//                    }
//                }

                // OFFERS: process offers addressed to me
                call.offers.forEach { (key, offer) ->
                    if (!key.endsWith("_$myUserId")) return@forEach
                    
                    val offerHash = "${key}_${offer.sdp.hashCode()}"
                    if (!handledOfferKeys.add(offerHash)) return@forEach

                    val senderId = offer.senderId
                    Log.d(TAG, "[$myUserId]: OFFER received from $senderId (key=$key)")

                    if (!isAnswered) {
                        isAnswered = true
                        timeoutJob?.cancel()
                    }
                    //callStateManager.updateState("CONNECTING")

                    ensurePeerConnection(senderId)

                    webRTCClient.onRemoteOfferReceived(senderId, offer.sdp) { answer ->
                        viewModelScope.launch {
                            if (!isEnding) {
                                signalingClient.sendAnswer(callId, myUserId, senderId, answer.description)
                                Log.d(TAG, "[$myUserId] ANSWER sent: $senderId (key: ${myUserId}_$senderId)")
                            }
                        }
                    }
                }

                // ANSWERS: process answers addressed to me
                call.answers.forEach { (key, answer) ->
                    val parts = key.split("_")
                    if (parts.size != 2) return@forEach

                    val senderId = parts[0]
                    val receiverId = parts[1]

                    if (receiverId != myUserId) return@forEach
                    
                    val answerHash = "${key}_${answer.hashCode()}"
                    if (!handledAnswerKeys.add(answerHash)) return@forEach

                    Log.d(TAG, "[$myUserId]: ANSWER received from $senderId (key=$key)")
                    if (!isAnswered) {
                        isAnswered = true
                        timeoutJob?.cancel()
                    }
                    //callStateManager.updateState("CONNECTING")

                    webRTCClient.onAnswerReceived(senderId, answer)
                }

                Log.d(TAG, "[$myUserId] Status: peers=${peerCreated.toList()}, offers=${handledOfferKeys.toList()}, answers=${handledAnswerKeys.toList()}")
            }
        }

        participantJob = viewModelScope.launch {
            signalingClient.listenForParticipants(callId).collect { (peerId, isJoin) ->

                if (peerId == myUserId) return@collect

                if (isJoin) {


                    _activeParticipants.update { it + peerId }

                    if (peerCreated.contains(peerId)) return@collect

                    val iAmInitiator = myUserId < peerId

                    if (iAmInitiator) {
                        Log.d(TAG, "[$myUserId] JOIN → connecting to $peerId")
                        enqueue(peerId)
                    } else {
                        Log.d(TAG, "[$myUserId] JOIN → waiting for $peerId")
                    }

                } else {

                    _activeParticipants.update { it - peerId }
                    Log.d(TAG, "[$myUserId] LEAVE → removing $peerId")

                    webRTCClient.removePeerConnection(peerId)
                    peerCreated.remove(peerId)
                    connectionQueue.remove(peerId)
                }
            }
        }

        iceOutgoingJob = viewModelScope.launch {
            webRTCClient.iceCandidateFlow.collect { (candidate, userId) ->
                if (!isEnding) {
                    signalingClient.sendICECandidate(
                        callId, userId,
                        IceCandidateModel(
                            sdp = candidate.sdp,
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex,
                            senderId = myUserId
                        )
                    )
                }
            }
        }

        iceIncomingJob = viewModelScope.launch {
            signalingClient.listenForCandidate(callId, myUserId).collect { (candidate, fromUserId) ->
                webRTCClient.addIceCandidate(fromUserId, candidate)
            }
        }
    }

    private fun startTimer() {
        if (timerStarted) return
        timerStarted = true
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _callTime.value++
            }
        }
    }

    fun toggleCamera() {
        webRTCClient.switchCamera()
    }

    fun toggleVideo() {
        val newState = !_isVideoEnabled.value
        _isVideoEnabled.value = newState

        if (newState) {
            webRTCClient.enableVideo()
        } else {
            webRTCClient.disableVideo()
        }
        updateMyMediaState()
    }

//        activeCallId?.let { id ->
//            viewModelScope.launch {
//                signalingClient.updateRemoteVideoState(id, isCaller, newState)
//            }
//        }

    fun refreshAudio() {
        webRTCClient.enableAllAudio()
    }

    fun toggleMute() {
        val newState = !_isMuted.value
        _isMuted.value = newState
        webRTCClient.toggleMute(newState)
        updateMyMediaState()
    }

    private fun updateMyMediaState() {
        activeCallId?.let { id ->
            viewModelScope.launch {
                signalingClient.updateMediaState(
                    id, 
                    myUserId, 
                    MediaState(muted = _isMuted.value, videoEnabled = _isVideoEnabled.value)
                )
            }
        }
    }

    fun endCall(callId: String) {
        if (isEnding) return

        val currentStatus = callStateManager.callState.value
        val finalStatus = when {
            currentStatus == "MISSED" -> "missed"
            currentStatus == "BUSY" -> "busy"
            currentStatus == "REJECTED" -> "rejected"
            !isAnswered -> "missed"
            else -> "completed"
        }
        saveHistoryIfNeeded(finalStatus)

        isEnding = true
        viewModelScope.launch {
            try {
                signalingClient.endCall(callId)
                webRTCClient.closeConnection()
                callStateManager.updateState(
                    if (currentStatus in listOf("MISSED", "BUSY", "REJECTED")) currentStatus else "ENDED"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error ending call", e)
            }

            _events.trySend(UiEvent.EndCall)
            _callEnded.value = true
            cleanupJobs()
            signalingClient.cleanupCallData(callId)
            _activeParticipants.update { emptySet() }
            activeCallId = null
            callStateManager.updateState("IDLE")
            isEnding = false
        }
    }

    fun leaveCall(callId: String) {
        if (isEnding) return

        isEnding = true

        viewModelScope.launch {
            try {
                Log.d(TAG, "[$myUserId] Leaving call $callId")

                signalingClient.removeParticipant(callId, myUserId)

                webRTCClient.closeConnection()

            } catch (e: Exception) {
                Log.e(TAG, "Error leaving call", e)
            }

            _events.trySend(UiEvent.EndCall)
            _callEnded.value = true

            cleanupJobs()
            _activeParticipants.update { emptySet() }
            activeCallId = null
            callStateManager.updateState("IDLE")

            isEnding = false
        }
    }

    private fun finishCall(callId: String, statusString: String = "ENDED") {
        if (isEnding) return

        val finalStatus = when (statusString) {
            "BUSY" -> "busy"
            "REJECTED" -> "rejected"
            "MISSED" -> "missed"
            else -> if (isAnswered) "completed" else "missed"
        }
        saveHistoryIfNeeded(finalStatus)

        isEnding = true
        callStateManager.updateState(statusString)
        viewModelScope.launch {
            webRTCClient.closeConnection()
            _events.trySend(UiEvent.EndCall)
            _callEnded.value = true
            cleanupJobs()
            _activeParticipants.update { emptySet() }
            signalingClient.cleanupCallData(callId)
            activeCallId = null
            callStateManager.updateState("IDLE")
            isEnding = false
        }
    }

    fun onAddParticipantClicked() {
        loadAvailableUsers(myUserId, _activeParticipants.value)
    }

    fun addParticipants(userIds: List<String>) {
        val callId = activeCallId ?: return

        viewModelScope.launch {
            userIds.forEach {
                signalingClient.addParticipant(callId, it)
            }
        }
    }

    fun loadAvailableUsers(currentUserId: String, existing: Set<String>) {
        viewModelScope.launch {
            userRepository.getAllUsers().collect { users ->

                val filtered = users.filter {
                    it.uid != currentUserId &&
                            !existing.contains(it.uid)
                }

                _availableUsers.value = filtered
            }
        }
    }


    private fun saveHistoryIfNeeded(finalStatus: String) {
        if (!isCaller) return
        if (activeCallerId.isEmpty() || activeReceiverId.isEmpty()) return

        val history = CallHistoryModel(
            callerId = activeCallerId,
            receiverId = activeReceiverId,
            duration = _callTime.value.toLong(),
            status = finalStatus
        )
        viewModelScope.launch {
            try {
                signalingClient.saveCallHistory(history)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save history", e)
            }
        }
    }

    private fun cleanupJobs() {
        signalingJob?.cancel()
        signalingJob = null

        iceOutgoingJob?.cancel()
        iceOutgoingJob = null

        participantJob?.cancel()
        participantJob = null

        iceIncomingJob?.cancel()
        iceIncomingJob = null

        timerJob?.cancel()
        timerJob = null

        timeoutJob?.cancel()
        timeoutJob = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "callvm cleared")
    }
}

sealed class UiEvent {
    object EndCall : UiEvent()
}