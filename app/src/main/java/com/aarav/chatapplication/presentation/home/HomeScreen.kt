package com.aarav.chatapplication.presentation.home

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.aarav.chatapplication.R
import com.aarav.chatapplication.presentation.call.CallViewModel
import com.aarav.chatapplication.presentation.chat.formatTimestamp
import com.aarav.chatapplication.presentation.components.CreateChatModalSheet
import com.aarav.chatapplication.presentation.components.CustomBottomSheet
import com.aarav.chatapplication.presentation.components.MyAlertDialog
import com.aarav.chatapplication.presentation.model.DirectChatEntry
import com.aarav.chatapplication.presentation.model.GroupChatEntry
import com.aarav.chatapplication.presentation.navigation.BottomNavigation
import com.aarav.chatapplication.ui.theme.hankenGrotesk
import com.posthog.PostHog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    callViewModel: CallViewModel,
    navController: NavController,
    navigateToChat: (String, String, String) -> Unit,
    navigateToGroupChat: (String, String, String) -> Unit,
    navigateToCreateGroup: (String) -> Unit,
    onLogout: () -> Unit,
    homeScreenVM: HomeScreenVM
) {

    val uiState by homeScreenVM.uiState.collectAsState()

    val currentUser by homeScreenVM.currentUser.collectAsState()

    Log.i("USER", "userId: ${uiState.userId}")
    LaunchedEffect(uiState.userId) {
        uiState.userId?.let {
            homeScreenVM.observeChatList(it)
        }
        Log.i("CHAT", "chatList : " + uiState.chatList.toString())
    }

    var showCreateChatModal by remember {
        mutableStateOf(false)
    }

    MyAlertDialog(
        shouldShowDialog = uiState.showErrorDialog,
        onDismissRequest = { homeScreenVM.clearError() },
        title = "Error",
        message = uiState.error ?: "",
        confirmButtonText = "Dismiss"
    ) {
        homeScreenVM.clearError()
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    LaunchedEffect(uiState.userId) {
        homeScreenVM.getUserId()
    }

    AnimatedVisibility(showCreateChatModal) {
        CustomBottomSheet(
            sheetState = sheetState,
            onDismiss = {
                showCreateChatModal = false
            },
            title = "Create New Chat"
        ) {
            Card(
                onClick = {
                    showCreateChatModal = false
                    uiState.userId?.let { userId ->
                        navigateToCreateGroup(userId)
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.create_chat),
                                contentDescription = "create group",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Text(
                            "New Group",
                            fontFamily = hankenGrotesk,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Create a group chat with multiple people",
                            fontFamily = hankenGrotesk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Text(
                "Direct Message",
                fontFamily = hankenGrotesk,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )

            CreateChatModalSheet(
                uiState.userList,
                onDismiss = { showCreateChatModal = false }) { receiverId ->
                uiState.userId?.let { userId ->
                    navigateToChat(receiverId, userId, uiState.currentUserName)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Messages",
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        fontFamily = hankenGrotesk,
                        color = Color(0xFF575459)
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            showCreateChatModal = true
                            PostHog.capture(
                                event = "button_clicked",
                                properties = mapOf(
                                    "button_name" to "create_new_chat"
                                )
                            )
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.create_chat),
                            contentDescription = "create chat",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigation(navController)
        }
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .padding(it)
                    .padding(bottom = 88.dp)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(it)
            ) {

                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Text(
                            "Recents",
                            fontFamily = hankenGrotesk,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                items(uiState.chatList) { entry ->
                    when (entry) {
                        is DirectChatEntry -> {
                            DirectChatItem(entry) {
                                uiState.userId?.let { userId ->
                                    navigateToChat(entry.otherUserId, userId, uiState.currentUserName)
                                }
                            }
                        }

                        is GroupChatEntry -> {
                            GroupChatItem(entry) {
                                uiState.userId?.let { userId ->
                                    navigateToGroupChat(
                                        entry.chatId,
                                        userId,
                                        uiState.currentUserName
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DirectChatItem(
    entry: DirectChatEntry,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = entry.otherUserName.take(1).uppercase(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = hankenGrotesk,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.otherUserName,
                    fontFamily = hankenGrotesk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = formatTimestamp(entry.lastTimestamp),
                    fontFamily = hankenGrotesk,
                    fontSize = 12.sp,
                    color = if (entry.unreadCount > 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.lastMessage.ifEmpty { "No messages yet" },
                    fontFamily = hankenGrotesk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    fontWeight = if (entry.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (entry.unreadCount > 0)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                if (entry.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (entry.unreadCount > 99) "99+" else entry.unreadCount.toString(),
                                fontFamily = hankenGrotesk,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 86.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        thickness = 0.5.dp
    )
}

@Composable
fun GroupChatItem(
    entry: GroupChatEntry,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Surface(
            modifier = Modifier.size(54.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = entry.groupName.take(1).uppercase(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = hankenGrotesk,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.groupName,
                    fontFamily = hankenGrotesk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (entry.lastTimestamp > 0) {
                    Text(
                        text = formatTimestamp(entry.lastTimestamp),
                        fontFamily = hankenGrotesk,
                        fontSize = 12.sp,
                        color = if (entry.unreadCount > 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            val displayMessage = when {
                entry.lastSenderName.isNotEmpty() && entry.lastMessage.isNotEmpty() ->
                    "${entry.lastSenderName}: ${entry.lastMessage}"
                entry.lastMessage.isNotEmpty() -> entry.lastMessage
                else -> "No messages yet"
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayMessage,
                    fontFamily = hankenGrotesk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    fontWeight = if (entry.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (entry.unreadCount > 0)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                if (entry.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (entry.unreadCount > 99) "99+" else entry.unreadCount.toString(),
                                fontFamily = hankenGrotesk,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 86.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        thickness = 0.5.dp
    )
}