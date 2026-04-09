package com.aarav.chatapplication.presentation.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.aarav.chatapplication.presentation.auth.AuthScreen
import com.aarav.chatapplication.presentation.chat.ChatScreen
import com.aarav.chatapplication.domain.repository.AuthRepository
import com.aarav.chatapplication.presentation.call.CallScreen
import com.aarav.chatapplication.presentation.group.CreateGroupScreen
import com.aarav.chatapplication.presentation.group.GroupChatScreen
import com.aarav.chatapplication.presentation.home.HomeScreen
import com.aarav.chatapplication.presentation.profile.ProfileScreen
import com.aarav.chatapplication.utils.generateChatId

@Composable
fun NavGraph(
    navHostController: NavHostController,
    authRepository: AuthRepository,
    userId: String?,
    modifier: Modifier
) {

    val isLoggedIn = authRepository.isLoggedIn()

    NavHost(
        navHostController,
        startDestination = if(isLoggedIn) NavRoute.Home.path else NavRoute.Auth.path
    ) {
        addHomeScreen(navHostController, this)
        addChatScreen(navHostController, this, userId ?: "")
        addGroupChatScreen(navHostController, this)
        addCreateGroupScreen(navHostController, this)
        addCallScreen(navHostController, this)
        addAuthScreen(navHostController, this)
        addProfileScreen(navHostController, this)
    }
}

fun addHomeScreen(navController: NavController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable(
        route = NavRoute.Home.path,
    ) {

        HomeScreen(
            navigateToChat = {
                receiverId, userId ->

                navController.navigate(NavRoute.Chat.createRoute(receiverId, userId))
            },
            navigateToGroupChat = { groupId, userId, senderName ->
                navController.navigate(NavRoute.GroupChat.createRoute(groupId, userId, senderName))
            },
            navigateToCreateGroup = { userId ->
                navController.navigate(NavRoute.CreateGroup.createRoute(userId))
            },
            navigateToCall = {
                callId, callerId, receiverId, isCaller ->
                navController.navigate(NavRoute.Call.createRoute(callId, callerId, receiverId, isCaller))
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
        ProfileScreen()
    }
}

fun addChatScreen(navController: NavController, navGraphBuilder: NavGraphBuilder, userId: String) {
    navGraphBuilder.composable(
        route = NavRoute.Chat.path.plus("/{receiverId}/{userId}"),
        arguments =
            listOf(
                navArgument("receiverId") {
                    type = NavType.StringType
                },
                navArgument("userId") {
                    type = NavType.StringType
                }
            )
    ) {

        val receiverId = it.arguments?.getString("receiverId").toString()
        val userId = it.arguments?.getString("userId").toString()
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
            myId = userId,
            navigateToCall = {
                navController.navigate(
                    NavRoute.Call.createRoute(
                        callId = chatId,
                        callerId = userId,
                        receiverId = receiverId,
                        isCaller = true
                    )
                )
            },
            chatViewModel = hiltViewModel()
        )
    }
}

fun addGroupChatScreen(navController: NavController, navGraphBuilder: NavGraphBuilder) {
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
            viewModel = hiltViewModel()
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


fun addCallScreen(navController: NavController, navGraphBuilder: NavGraphBuilder) {
    navGraphBuilder.composable(
        route = NavRoute.Call.path.plus("/{callId}/{callerId}/{receiverId}/{isCaller}"),
        arguments = listOf(
            navArgument("callId") { type = NavType.StringType },
            navArgument("callerId") { type = NavType.StringType },
            navArgument("receiverId") { type = NavType.StringType },
            navArgument("isCaller") { type = NavType.BoolType }
        )
    ) {
        val groupId = it.arguments?.getString("callId").toString()
        val userId = it.arguments?.getString("callerId").toString()
        val receiverId = it.arguments?.getString("receiverId").toString()
        val senderName = it.arguments?.getBoolean("isCaller")

        CallScreen(
            callId = groupId,
            callerId = userId,
            receiverId = receiverId,
            isCaller = senderName ?: false,
            viewModel = hiltViewModel()
        )
    }
}