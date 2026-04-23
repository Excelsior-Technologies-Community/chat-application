package com.aarav.chatapplication.presentation.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.aarav.chatapplication.domain.repository.AuthRepository
import com.aarav.chatapplication.presentation.auth.AuthScreen
import com.aarav.chatapplication.presentation.call.GroupCallScreen
import com.aarav.chatapplication.presentation.call.CallViewModel
import com.aarav.chatapplication.presentation.call.OneToOneCallScreen
import com.aarav.chatapplication.presentation.chat.ChatScreen
import com.aarav.chatapplication.presentation.group.CreateGroupScreen
import com.aarav.chatapplication.presentation.group.GroupChatScreen
import com.aarav.chatapplication.presentation.home.HomeScreen
import com.aarav.chatapplication.presentation.profile.ProfileScreen
import com.aarav.chatapplication.presentation.call_history.CallHistoryScreen
import com.aarav.chatapplication.utils.generateChatId

@Composable
fun NavGraph(
    navHostController: NavHostController,
    authRepository: AuthRepository,
    callViewModel: CallViewModel,
    userId: String?,
    modifier: Modifier = Modifier
) {

    val isLoggedIn = authRepository.isLoggedIn()

    NavHost(
        navHostController,
        startDestination = if (isLoggedIn) NavRoute.Home.path else NavRoute.Auth.path
    ) {
        addHomeScreen(navHostController, this, callViewModel)
        addChatScreen(navHostController, this, userId ?: "", callViewModel)
        addGroupChatScreen(navHostController, this, callViewModel)
        addCreateGroupScreen(navHostController, this)
        addGroupCallScreen(navHostController, this, callViewModel)
        addOneToOneCallScreen(navHostController, this, callViewModel)
        addAuthScreen(navHostController, this)
        addProfileScreen(navHostController, this)
        addCallHistoryScreen(navHostController, this)
        addGroupInfoScreen(navHostController, this)
        addChatInfoScreen(navHostController, this)
    }
}

fun addHomeScreen(
    navController: NavController,
    navGraphBuilder: NavGraphBuilder,
    callViewModel: CallViewModel
) {
    navGraphBuilder.composable(
        route = NavRoute.Home.path,
    ) {

        HomeScreen(
            callViewModel = callViewModel,
            navController,
            navigateToChat = { receiverId, userId, currentUsername ->

                navController.navigate(
                    NavRoute.Chat.createRoute(
                        receiverId,
                        userId,
                        currentUsername
                    )
                )
            },
            navigateToGroupChat = { groupId, userId, senderName ->
                navController.navigate(NavRoute.GroupChat.createRoute(groupId, userId, senderName))
            },
            navigateToCreateGroup = { userId ->
                navController.navigate(NavRoute.CreateGroup.createRoute(userId))
            },
            onLogout = {
                navController.navigate(NavRoute.Auth.path) {
                    popUpTo(NavRoute.Home.path) {
                        inclusive = true
                    }
                }
            },
            homeScreenVM = hiltViewModel()
        )
    }
}

fun addAuthScreen(navController: NavController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable(
        route = NavRoute.Auth.path
    ) {
        AuthScreen(
            navigateToHome = {
                navController.navigate(NavRoute.Home.path)
            },
            viewModel = hiltViewModel()
        )
    }
}

fun addProfileScreen(navController: NavController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable(
        route = NavRoute.Profile.path
    ) {
        ProfileScreen(
            navController,
            viewModel = hiltViewModel()
        )
    }
}

fun addCallHistoryScreen(navController: NavController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable(
        route = NavRoute.CallHistory.path
    ) {
        CallHistoryScreen(
            navController = navController,
            viewModel = hiltViewModel()
        )
    }
}

fun addChatScreen(navController: NavController, navGraphBuilder: NavGraphBuilder, userId: String, callViewModel: CallViewModel) {
    navGraphBuilder.composable(
        route = NavRoute.Chat.path.plus("/{receiverId}/{userId}/{currentUsername}"),
        arguments =
            listOf(
                navArgument("receiverId") {
                    type = NavType.StringType
                },
                navArgument("userId") {
                    type = NavType.StringType
                },
                navArgument("currentUsername") {
                    type = NavType.StringType
                }
            )
    ) {

        val receiverId = it.arguments?.getString("receiverId").toString()
        val userId = it.arguments?.getString("userId").toString()
        val currentUsername = it.arguments?.getString("currentUsername").toString()
        Log.i("MYTAG", "rec: " + receiverId)
        Log.i("MYTAG", "my: " + userId)

        val chatId = generateChatId(userId, receiverId)

        Log.i("MYTAG", "chat: " + chatId)

        ChatScreen(
            back = {
                navController.popBackStack()
            },
            chatId = chatId,
            otherUserId = receiverId,
            currentUsername = currentUsername,
            myId = userId,
            navigateToCall = { isVideoCall ->
                navController.navigate(
                    NavRoute.OneToOne.createRoute(
                        callId = chatId,
                        myUserId = userId,
                        callerName = currentUsername,
                        isCaller = true,
                        isVideoCall = isVideoCall
                    )
                )
            },
            onInfoClick = {
                navController.navigate(NavRoute.ChatInfo.createRoute(receiverId))
            },
            chatViewModel = hiltViewModel(),
            callViewModel = callViewModel
        )
    }
}

fun addGroupChatScreen(
    navController: NavController,
    navGraphBuilder: NavGraphBuilder,
    callViewModel: CallViewModel
) {
    navGraphBuilder.composable(
        route = NavRoute.GroupChat.path.plus("/{groupId}/{userId}/{senderName}"),
        arguments = listOf(
            navArgument("groupId") { type = NavType.StringType },
            navArgument("userId") { type = NavType.StringType },
            navArgument("senderName") { type = NavType.StringType }
        )
    ) {
        val groupId = it.arguments?.getString("groupId").toString()
        val userId = it.arguments?.getString("userId").toString()
        val senderName = it.arguments?.getString("senderName").toString()

        GroupChatScreen(
            groupId = groupId,
            myId = userId,
            senderName = senderName,
            back = { navController.popBackStack() },
            onCallStart = { isVideoCall ->
              navController.navigate(
                  NavRoute.GroupCall.createRoute(
                      callId = groupId,
                      myUserId = userId,
                      callerName = senderName,
                      isCaller = true,
                      isVideoCall = isVideoCall
                  )
              )
            },
            onInfoClick = {
                navController.navigate(NavRoute.GroupInfo.createRoute(groupId, userId))
            },
            viewModel = hiltViewModel(),
            callViewModel = callViewModel
        )
    }
}

fun addCreateGroupScreen(navController: NavController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable(
        route = NavRoute.CreateGroup.path.plus("/{userId}"),
        arguments = listOf(
            navArgument("userId") { type = NavType.StringType }
        )
    ) {
        val userId = it.arguments?.getString("userId").toString()

        CreateGroupScreen(
            userId = userId,
            onGroupCreated = { groupId ->
                navController.navigate(
                    NavRoute.GroupChat.createRoute(groupId, userId, "You")
                ) {
                    popUpTo(NavRoute.Home.path)
                }
            },
            onBack = { navController.popBackStack() },
            viewModel = hiltViewModel()
        )
    }
}

fun addGroupCallScreen(
    navController: NavController,
    navGraphBuilder: NavGraphBuilder,
    callViewModel: CallViewModel
) {
    navGraphBuilder.composable(
        route = NavRoute.GroupCall.path.plus("/{callId}/{myUserId}/{callerName}/{isCaller}/{videoCall}"),
        arguments = listOf(
            navArgument("callId") { type = NavType.StringType },
            navArgument("myUserId") { type = NavType.StringType },
            navArgument("callerName") { type = NavType.StringType },
            navArgument("isCaller") { type = NavType.BoolType },
            navArgument("videoCall") { type = NavType.BoolType }
        )
    ) {
        val groupId = it.arguments?.getString("callId").toString()
        val myUserId = it.arguments?.getString("myUserId").toString()
        val callerName = it.arguments?.getString("callerName").toString()
        val senderName = it.arguments?.getBoolean("isCaller")
        val isVideoCall = it.arguments?.getBoolean("videoCall")

        GroupCallScreen(
            callId = groupId,
            myUserId = myUserId,
            callerName = callerName,
            isCaller = senderName ?: false,
            isVideoCall = isVideoCall ?: false,
            onCallEnd = {
                navController.popBackStack()
            },
            viewModel = callViewModel
        )
    }
}

fun addOneToOneCallScreen(
    navController: NavController,
    navGraphBuilder: NavGraphBuilder,
    callViewModel: CallViewModel
) {
    navGraphBuilder.composable(
        route = NavRoute.OneToOne.path.plus("/{callId}/{myUserId}/{callerName}/{isCaller}/{videoCall}"),
        arguments = listOf(
            navArgument("callId") { type = NavType.StringType },
            navArgument("myUserId") { type = NavType.StringType },
            navArgument("callerName") { type = NavType.StringType },
            navArgument("isCaller") { type = NavType.BoolType },
            navArgument("videoCall") { type = NavType.BoolType }
        )
    ) {
        val callId = it.arguments?.getString("callId").toString()
        val myUserId = it.arguments?.getString("myUserId").toString()
        val callerName = it.arguments?.getString("callerName").toString()
        val isCaller = it.arguments?.getBoolean("isCaller")
        val isVideoCall = it.arguments?.getBoolean("videoCall")

        OneToOneCallScreen(
            callId = callId,
            myUserId = myUserId,
            callerName = callerName,
            isCaller = isCaller ?: false,
            isVideoCall = isVideoCall ?: false,
            onCallEnd = {
                navController.popBackStack()
            },
            viewModel = callViewModel
        )
    }
}

fun addGroupInfoScreen(navController: NavController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable(
        route = NavRoute.GroupInfo.path.plus("/{groupId}/{currentUserId}"),
        arguments = listOf(
            navArgument("groupId") { type = NavType.StringType },
            navArgument("currentUserId") { type = NavType.StringType }
        )
    ) {
        val groupId = it.arguments?.getString("groupId").toString()
        val currentUserId = it.arguments?.getString("currentUserId").toString()

        com.aarav.chatapplication.presentation.group_info.GroupInfoScreen(
            groupId = groupId,
            currentUserId = currentUserId,
            onBack = { navController.popBackStack() },
            viewModel = hiltViewModel(),
            onNavigateToChatList = {
                navController.navigate(NavRoute.Home.path) {
                    popUpTo(NavRoute.Home.path) { inclusive = true }
                }
            }
        )
    }
}

fun addChatInfoScreen(navController: NavController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable(
        route = NavRoute.ChatInfo.path.plus("/{userId}"),
        arguments = listOf(
            navArgument("userId") { type = NavType.StringType }
        )
    ) {
        val userId = it.arguments?.getString("userId").toString()

        com.aarav.chatapplication.presentation.chat_info.ChatInfoScreen(
            userId = userId,
            onBack = { navController.popBackStack() },
            viewModel = hiltViewModel()
        )
    }
}