package com.aarav.chatapplication.presentation.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aarav.chatapplication.R
import com.aarav.chatapplication.data.model.CallModel
import com.aarav.chatapplication.data.model.Message
import com.aarav.chatapplication.presentation.call.CallViewModel
import com.aarav.chatapplication.presentation.components.MessageStatusIcon
import com.aarav.chatapplication.presentation.components.MyAlertDialog
import com.aarav.chatapplication.presentation.home.ChatViewModel
import com.aarav.chatapplication.ui.theme.hankenGrotesk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    myId: String,
    otherUserId: String,
    currentUsername: String,
    back: () -> Unit,
    navigateToCall: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    chatViewModel: ChatViewModel,
    callViewModel: CallViewModel
) {

    val uiState by chatViewModel.uiState.collectAsState()

    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var isFocused by remember { mutableStateOf(false) }
    val isKeyboardOpen = isKeyboardOpen()

    LaunchedEffect(Unit) {
        chatViewModel.observeMessages(chatId, myId)
        chatViewModel.observePresence(otherUserId)
        chatViewModel.getUser(otherUserId)
        chatViewModel.isChatCreated(chatId, myId)
    }

    LaunchedEffect(isKeyboardOpen) {
        if (isKeyboardOpen) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    val scope = rememberCoroutineScope()

    MyAlertDialog(
        shouldShowDialog = uiState.showErrorDialog,
        onDismissRequest = { chatViewModel.clearError() },
        title = "Error",
        message = uiState.error ?: "Something went wrong",
        confirmButtonText = "Dismiss",
    ) {
        chatViewModel.clearError()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {

        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(it)
                .fillMaxSize()
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .padding(top = 0.dp)
                        .fillMaxWidth()
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
                                    chatViewModel.onTypingStopped()
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
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = uiState.user?.name?.take(1)?.uppercase() ?: "?",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = hankenGrotesk,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
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
                                uiState.user?.name ?: "",
                                fontFamily = hankenGrotesk,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (uiState.isOtherUserTyping) {
                                    Text(
                                        "typing...",
                                        fontFamily = hankenGrotesk,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else {
                                    when {
                                        uiState.presence == null -> ""
                                        uiState.presence!!.online -> {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFF00FF85),
                                                modifier = Modifier.size(8.dp)
                                            ) { }

                                            Text(
                                                "Online",
                                                fontFamily = hankenGrotesk,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00FF85)
                                            )
                                        }

                                        !uiState.presence!!.online -> {
                                            Text(
                                                "last active at ${formatTimestamp(uiState.presence!!.lastSeen)}",
                                                fontFamily = hankenGrotesk,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = {

                                val participants = listOf(myId, otherUserId).associateWith { true }

                                val call = CallModel(
                                    callId = chatId,
                                    callerId = myId,
                                    callerName = currentUsername,
                                    participants = participants,
                                    groupCall = false,
                                    videoCall = true
                                )

                                scope.launch {
                                    callViewModel.startCall(call, myId)

                                    delay(300)

                                    navigateToCall(true)
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

                                val participants = listOf(myId, otherUserId).associateWith { true }

                                val call = CallModel(
                                    callId = chatId,
                                    callerId = myId,
                                    callerName = currentUsername,
                                    participants = participants,
                                    groupCall = false,
                                    videoCall = false
                                )

                                scope.launch {
                                    callViewModel.startCall(call, myId)

                                    delay(300)

                                    navigateToCall(false)
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

                LazyColumn(
                    state = listState,
                    flingBehavior = ScrollableDefaults.flingBehavior(),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .weight(1f)
                ) {
                    items(uiState.messages) { chat ->
                        val isMine = chat.senderId == myId
                        ChatCard(chat, isMine)
                    }

                    item {
                        if (!uiState.isChatCreated) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 36.dp)
                            ) {
                                Text(
                                    "Say Hey to ${uiState.user?.name} and start a conversation",
                                    fontFamily = hankenGrotesk,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                TextTypeBox(
                    isFocused,
                    onFocusChange = { isFocused = it },
                    text,
                    onValueChange = { text = it },
                    onStartTyping = { chatViewModel.onTypingStarted() },
                    onStopTyping = { chatViewModel.onTypingStopped() },
                    error = uiState.messageError,
                    Modifier
                ) {
                    chatViewModel.sendMessages(otherUserId, text)
                    text = ""
                    chatViewModel.onTypingStopped()
                }
            }

            if (uiState.messages.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 92.dp)
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
    }
}

@Composable
fun TextTypeBox(
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    text: String,
    onValueChange: (String) -> Unit,
    onStartTyping: () -> Unit,
    onStopTyping: () -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .onFocusChanged { focusState ->
                    onFocusChange(focusState.isFocused)
                },
            value = text,
            onValueChange = {
                onValueChange(it)
                if (it.isNotBlank()) {
                    onStartTyping()
                } else {
                    onStopTyping()
                }
            },
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            placeholder = {
                if (error == null) {
                    Text(
                        "Type here...",
                        fontFamily = hankenGrotesk,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    Text(
                        error,
                        fontFamily = hankenGrotesk,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VerticalDivider(
                        modifier = Modifier
                            .width(2.dp)
                            .height(28.dp)
                    )

                    Icon(
                        painter = painterResource(R.drawable.send),
                        contentDescription = "send",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onClick() }
                    )
                }
            }
        )
    }
}

@Composable
fun ChatCard(
    message: Message,
    isMine: Boolean
) {
    val bg =
        if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val content =
        if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val alignment = if (isMine) Alignment.End else Alignment.Start

    Box(
        modifier = Modifier.fillMaxWidth(),
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
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = bg
                    ),
                    modifier = Modifier,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            message.text,
                            fontFamily = hankenGrotesk,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W700,
                            modifier = Modifier,
                            color = content
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
                    modifier = Modifier,
                    color = content
                )

                Spacer(Modifier.width(4.dp))

                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                ) {
                    if (isMine) {
                        MessageStatusIcon(message.status)
                    }
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val df = SimpleDateFormat("HH:mm", Locale.getDefault())
    return df.format(Date(timestamp))
}

fun formatDateWise(timestamp: Long): String {
    val df = SimpleDateFormat("dd MMM YYYY", Locale.getDefault())
    return df.format(Date(timestamp))
}

fun buildRelativeTime(timestamp: Long): String {
    val nowCal = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply {
        timeInMillis = timestamp
    }

    val nowDay = nowCal.get(Calendar.YEAR) * 1000 +
            nowCal.get(Calendar.DAY_OF_YEAR)

    val msgDay = msgCal.get(Calendar.YEAR) * 1000 +
            msgCal.get(Calendar.DAY_OF_YEAR)

    val diffDays = nowDay - msgDay

    return when (diffDays) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> formatDateWise(timestamp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun isKeyboardOpen(): Boolean {
    val imeInsets = WindowInsets.isImeVisible
    return imeInsets
}