package com.aarav.chatapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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

        var currentUserId: String? = firebaseAuth.currentUser?.uid

        setContent {
            AppTheme {
                val navController = rememberNavController()

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val navItems = listOf(
                    NavRoute.Home.path,
                    NavRoute.Profile.path,
                )

                LaunchedEffect(Unit) {
                    currentUserId = firebaseAuth.currentUser?.uid
                }

                var showCallBanner by remember {
                    mutableStateOf(false)
                }


                val show = currentRoute in navItems

                val context = LocalContext.current

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

                    if(!isCameraPermissionGranted && isAudioPermissionGranted) {
                        launcher2.launch(cameraPermission)
                    }
                }

                val mainViewModel: MainVM = hiltViewModel()

                val callViewModel: CallViewModel = hiltViewModel()

                val callState by callViewModel.callState.collectAsState()

                var callInfo by remember {
                    mutableStateOf<CallModel?>(null)
                }

                LaunchedEffect(currentUserId) {
                    currentUserId?.let {
                        mainViewModel.listenForIncomingCalls(it)

                        mainViewModel.incomingCall.collect { call ->
                            if (callInfo?.callId != call.callId) {
                                showCallBanner = true
                                callInfo = call
                            }
                        }
                    }
                }


                /*
                navController.navigate("call/${call.callId}/${call.callerId}/${call.receiverId}/${false}")
                 */

                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {


                    NavGraph(
                        navController,
                        authRepository,
                        callViewModel,
                        currentUserId
                    )

                    if (showCallBanner) {
                        IncomingCallBanner(
                            callerName = "Test",
                            onAccept = {
                                showCallBanner = false
                                navController.navigate("call/${callInfo?.callId}/${callInfo?.callerId}/${callInfo?.receiverId}/${false}")
                            },
                            onDecline = {
                                showCallBanner = false
                                callInfo?.let {
                                    callViewModel.endCall(it.callId)
                                }
                            },
                            modifier = Modifier
                                .align(
                                    androidx.compose.ui.Alignment.TopCenter
                                )
                                .padding(top = 48.dp)
                        )
                    }
                }
            }
        }
    }
}
