package com.aarav.chatapplication

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.domain.repository.AuthRepository
import com.aarav.chatapplication.domain.repository.PresenceRepository
import com.aarav.chatapplication.presentation.call.CallViewModel
import com.aarav.chatapplication.presentation.call.IncomingCallBanner
import com.aarav.chatapplication.presentation.navigation.NavGraph
import com.aarav.chatapplication.presentation.navigation.NavRoute
import com.aarav.chatapplication.ui.theme.AppTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var presenceRepository: PresenceRepository

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        ViewCompat.setOnApplyWindowInsetsListener(View(applicationContext)) { v, insets ->
            val systemBars =
                insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, View(applicationContext)).apply {

            isAppearanceLightNavigationBars = false
        }

        setContent {
            AppTheme {
                val context = LocalContext.current
                var currentUserId by remember { mutableStateOf(firebaseAuth.currentUser?.uid) }
                val navController = rememberNavController()

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val navItems = listOf(
                    NavRoute.Home.path,
                    NavRoute.Profile.path,
                )

                LaunchedEffect(Unit) {
                    firebaseAuth.addAuthStateListener {
                        currentUserId = it.currentUser?.uid
                    }
                }

                var showCallBanner by remember {
                    mutableStateOf(false)
                }

                val show = currentRoute in navItems

                val audioPermission = remember {
                    Manifest.permission.RECORD_AUDIO
                }

                val cameraPermission = remember {
                    Manifest.permission.CAMERA
                }

                var isAudioPermissionGranted = remember {
                    ContextCompat.checkSelfPermission(
                        context,
                        audioPermission
                    ) == PackageManager.PERMISSION_GRANTED
                }

                var isCameraPermissionGranted = remember {
                    ContextCompat.checkSelfPermission(
                        context,
                        cameraPermission
                    ) == PackageManager.PERMISSION_GRANTED
                }

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    isAudioPermissionGranted = granted
                }
                val launcher2 = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    isCameraPermissionGranted = granted
                }

                LaunchedEffect(isAudioPermissionGranted, isCameraPermissionGranted) {
                    if (!isAudioPermissionGranted) {
                        launcher.launch(audioPermission)
                    }

                    if (!isCameraPermissionGranted && isAudioPermissionGranted) {
                        launcher2.launch(cameraPermission)
                    }
                }

                val mainViewModel: MainVM = hiltViewModel()

                val callViewModel: CallViewModel = hiltViewModel()

                val call by mainViewModel.incomingCall.collectAsState()

                val callState by callViewModel.callState.collectAsState()
                val callEnded by callViewModel.callEnded.collectAsState()

                var callInfo by remember {
                    mutableStateOf<CallModel?>(null)
                }

                LaunchedEffect(call) {
                    call?.let {
                        callInfo = it
                    } ?: run {
                        callInfo = null
                    }
                }

                LaunchedEffect(callState) {
                    Log.d("CALL_STATE", callState)
                    if (callState == "IDLE") {
                        showCallBanner = false
                        callInfo = null
                        mainViewModel.clearIncomingCall()
                    }

                }

                LaunchedEffect(currentUserId) {
                    currentUserId?.let {

                        mainViewModel.listenForIncomingCalls(it)

                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {

                    NavGraph(
                        navController,
                        authRepository,
                        callViewModel,
                        currentUserId
                    )

                    if (call != null && callState == "IDLE") {
                        showCallBanner = true
                        IncomingCallBanner(
                            callerName = if (callInfo?.callerName.isNullOrBlank())
                                "User"
                            else if (!callInfo?.callerName.isNullOrBlank() && callInfo?.groupCall == true) {
                                "${callInfo?.callerName} started a group call"
                            } else
                                callInfo?.callerName!!,
                            onAccept = {
                                showCallBanner = false
                                callInfo?.let {
                                    navController.navigate(
                                        if (it.groupCall) {
                                            NavRoute.GroupCall
                                                .createRoute(
                                                    callId = it.callId,
                                                    myUserId = currentUserId ?: "",
                                                    callerName = it.callerName ?: "User",
                                                    isCaller = false,
                                                    isVideoCall = it.videoCall
                                                )
                                        } else {
                                            NavRoute.OneToOne
                                                .createRoute(
                                                    callId = it.callId,
                                                    myUserId = currentUserId ?: "",
                                                    callerName = it.callerName ?: "User",
                                                    isCaller = false,
                                                    isVideoCall = it.videoCall
                                                )
                                        }
                                    )

                                }
                            },
                            onDecline = {
                                showCallBanner = false
                                callInfo?.let {
                                    callViewModel.endCall(it.callId)
                                }
                                callInfo = null
                            },
                            modifier = Modifier
                                .align(
                                    Alignment.TopCenter
                                )
                                .padding(top = 48.dp)
                        )
                    }
                }
            }
        }
    }
}