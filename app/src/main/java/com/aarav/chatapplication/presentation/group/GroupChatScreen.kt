package com.aarav.chatapplication.presentation.group

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aarav.chatapplication.R
import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.data.model.GroupMessage
import com.aarav.chatapplication.presentation.call.CallViewModel
import com.aarav.chatapplication.presentation.chat.TextTypeBox
import com.aarav.chatapplication.presentation.chat.buildRelativeTime
import com.aarav.chatapplication.presentation.chat.formatTimestamp
import com.aarav.chatapplication.presentation.chat.isKeyboardOpen
import com.aarav.chatapplication.presentation.components.MessageStatusIcon
import com.aarav.chatapplication.presentation.components.MyAlertDialog
import com.aarav.chatapplication.ui.theme.hankenGrotesk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupChatScreen(
    groupId: String,
    myId: String,
    senderName: String,
    back: () -> Unit,
    onCallStart: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    viewModel: GroupChatViewModel,
    callViewModel: CallViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var isFocused by remember { mutableStateOf(false) }
    val isKeyboardOpen = isKeyboardOpen()

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.setSenderName(senderName)
        viewModel.observeGroup(groupId)
        viewModel.observeMessages(groupId, myId)
    }

    LaunchedEffect(isKeyboardOpen) {
        if (isKeyboardOpen && uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    MyAlertDialog(
        shouldShowDialog = uiState.showErrorDialog,
        onDismissRequest = { viewModel.clearError() },
        title = "Error",
        message = uiState.error ?: "Something went wrong",
        confirmButtonText = "Dismiss"
    ) {
        viewModel.clearError()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp)
                        .clickable { onInfoClick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    back()
                                    viewModel.onTypingStopped()
                                }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = "back",
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(18.dp)
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = uiState.group?.name?.take(1)?.uppercase() ?: "?",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = hankenGrotesk,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .weight(1f)
                        ) {
                            Text(
                                uiState.group?.name ?: "",
                                fontFamily = hankenGrotesk,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (uiState.typingUserIds.isNotEmpty()) {
                                    Text(
                                        "someone is typing...",
                                        fontFamily = hankenGrotesk,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else {
                                    val memberCount = uiState.group?.members?.filter { it.value }?.size ?: 0
                                    Text(
                                        "$memberCount active members",
                                        fontFamily = hankenGrotesk,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {

                                val members = uiState.group?.members
                                val participantList = members?.keys?.toList()

                                val final = participantList?.associateWith { true }

                                scope.launch {
                                    final?.let {
                                        val call = CallModel(
                                            callId = groupId,
                                            callerId = myId,
                                            callerName = senderName,
                                            participants = final,
                                            videoCall = true,
                                            groupCall = true,
                                        )

                                        callViewModel.startCall(
                                            call,
                                            myId
                                        )

                                        delay(300)

                                        onCallStart(true)
                                    }
                                }
                            },
                            modifier = Modifier
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.camera_on),
                                contentDescription = "video call",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {

                                val members = uiState.group?.members
                                val participantList = members?.keys?.toList()

                                val final = participantList?.associateWith { true }

                                scope.launch {
                                    final?.let {
                                        val call = CallModel(
                                            callId = groupId,
                                            callerId = myId,
                                            callerName = senderName,
                                            participants = it,
                                            videoCall = false,
                                            groupCall = true,
                                        )

                                        callViewModel.startCall(
                                            call,
                                            myId
                                        )

                                        delay(300)

                                        onCallStart(false)
                                    }
                                }
                            },
                            modifier = Modifier
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.phone),
                                contentDescription = "audio call",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                uiState.pinnedMessage?.let { pinned ->
                    PinnedMessageBanner(
                        message = pinned,
                        onUnpin = { viewModel.unpinMessage() },
                        isAdmin = uiState.group?.admins?.contains(myId) == true
                    )
                }

                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .weight(1f)
                ) {
                    LazyColumn(
                        state = listState,
                        flingBehavior = ScrollableDefaults.flingBehavior(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.messages, key = { it.messageId }) { message ->
                            val isMine = message.senderId == myId
                            val isAdmin = uiState.group?.admins?.contains(myId) == true
                            
                            GroupChatCard(
                                message = message,
                                isMine = isMine,
                                isAdmin = isAdmin,
                                onDelete = {
                                    viewModel.deleteMessage(message.messageId)
                                },
                                onPin = {
                                    viewModel.pinMessage(message.messageId)
                                }
                            )
                        }

                        item {
                            if (uiState.messages.isEmpty()) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 36.dp)
                                ) {
                                    Text(
                                        "Start a conversation in ${uiState.group?.name ?: "this group"}",
                                        fontFamily = hankenGrotesk,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.messages.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                        ) {
                            Text(
                                buildRelativeTime(uiState.messages.last().timestamp),
                                fontFamily = hankenGrotesk,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 24.dp)
                            )
                        }
                    }
                }

                if (uiState.group?.members?.get(myId) == true) {
                    TextTypeBox(
                        isFocused = isFocused,
                        onFocusChange = { isFocused = it },
                        text = text,
                        onValueChange = { text = it },
                        onStartTyping = { viewModel.onTypingStarted() },
                        onStopTyping = { viewModel.onTypingStopped() },
                        error = uiState.messageError,
                        modifier = Modifier
                    ) {
                        viewModel.sendMessage(text)
                        text = ""
                        viewModel.onTypingStopped()
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            "You can no longer send messages to this group.",
                            modifier = Modifier.padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontFamily = hankenGrotesk,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupChatCard(
    message: GroupMessage,
    isMine: Boolean,
    isAdmin: Boolean,
    onDelete: () -> Unit,
    onPin: () -> Unit
) {
    val bg = if (message.isDeleted) MaterialTheme.colorScheme.surfaceVariant
    else if (isMine) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.secondaryContainer
    
    val content = if (message.isDeleted) MaterialTheme.colorScheme.outline
    else if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSecondaryContainer
    
    val alignment = if (isMine) Alignment.End else Alignment.Start
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = alignment,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp)
        ) {
            Column(
                horizontalAlignment = alignment,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Box {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = bg),
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .combinedClickable(
                            onClick = { },
                            onLongClick = {
                                if (!message.isDeleted && (isMine || isAdmin)) {
                                    showMenu = true
                                }
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            if (!isMine && !message.isDeleted) {
                                Text(
                                    message.senderName,
                                    fontFamily = hankenGrotesk,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                            }

                            Text(
                                text = if (message.isDeleted) "This message was deleted" else message.text,
                                fontFamily = hankenGrotesk,
                                fontSize = 14.sp,
                                fontWeight = if (message.isDeleted) FontWeight.Normal else FontWeight.W700,
                                color = content,
                                fontStyle = if (message.isDeleted) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                            )
                        }
                    }

                    androidx.compose.material3.DropdownMenu(
                        offset = DpOffset(x = 0.dp, y = 12.dp),
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (isAdmin && !message.isDeleted) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { 
                                    Text(
                                        "Pin Message", 
                                        fontFamily = hankenGrotesk
                                    ) 
                                },
                                onClick = {
                                    onPin()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.pin),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp).rotate(270f)
                                    )
                                }
                            )
                        }

                        androidx.compose.material3.DropdownMenuItem(
                            text = { 
                                Text(
                                    "Delete for Everyone", 
                                    fontFamily = hankenGrotesk,
                                    color = MaterialTheme.colorScheme.error
                                ) 
                            },
                            onClick = {
                                onDelete()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.remove),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row {
                Text(
                    formatTimestamp(message.timestamp),
                    fontFamily = hankenGrotesk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W700,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(Modifier.width(4.dp))

                if (isMine && !message.isDeleted) {
                    Surface(color = Color.Transparent) {
                        MessageStatusIcon(message.status)
                    }
                }
            }
        }
    }
}
@Composable
fun PinnedMessageBanner(
    message: GroupMessage,
    onUnpin: () -> Unit,
    isAdmin: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.pin),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
                    .rotate(270f),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pinned Message",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = hankenGrotesk,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    fontFamily = hankenGrotesk,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            if (isAdmin) {
                IconButton(onClick = onUnpin, modifier = Modifier.size(24.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.remove),
                        contentDescription = "unpin",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
