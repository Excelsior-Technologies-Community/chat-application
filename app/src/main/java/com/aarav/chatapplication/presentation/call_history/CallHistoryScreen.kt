package com.aarav.chatapplication.presentation.call_history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.aarav.chatapplication.R
import com.aarav.chatapplication.data.model.CallHistoryModel
import com.aarav.chatapplication.presentation.chat.formatTimestamp
import com.aarav.chatapplication.presentation.navigation.BottomNavigation
import com.aarav.chatapplication.ui.theme.hankenGrotesk

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CallHistoryScreen(
    navController: NavController,
    viewModel: CallHistoryViewModel
) {
    val history by viewModel.callHistory.collectAsState()
    val usersMapping by viewModel.usersMapping.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentUserId = viewModel.currentUserId

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Call History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        fontFamily = hankenGrotesk,
                        color = Color(0xFF575459)
                    )
                }
            )
        },
        bottomBar = {
            BottomNavigation(navController)
        }
    ) { paddingValues ->

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }

            history.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No past calls", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    items(history) { call ->
                        CallHistoryItem(
                            call = call,
                            currentUserId = currentUserId ?: "",
                            usersMapping = usersMapping
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CallHistoryItem(
    call: CallHistoryModel,
    currentUserId: String,
    usersMapping: Map<String, String>
) {
    val isOutgoing = call.callerId == currentUserId
    var isExpanded by remember { mutableStateOf(false) }

    val displayNames = if (call.groupCall) {
        val otherParticipants = call.participants.keys.filter { it != currentUserId }
        otherParticipants.map { usersMapping[it] ?: "Unknown" }.joinToString(", ")
    } else {
        val otherId = if (isOutgoing) call.receiverId else call.callerId
        usersMapping[otherId] ?: "Unknown"
    }

    val iconRes = if (call.videoCall) R.drawable.camera_on else R.drawable.phone
    val directionIcon = if (isOutgoing) R.drawable.phone_outgoing else R.drawable.phone_incoming

    val callStatusColor = when (call.status.lowercase()) {
        "missed", "rejected", "busy" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(callStatusColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(directionIcon),
                        contentDescription = null,
                        tint = callStatusColor,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (call.groupCall) "Group Call" else displayNames.ifEmpty { "Unknown" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = hankenGrotesk,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = call.status.replaceFirstChar { it.uppercase() },
                            color = callStatusColor,
                            fontFamily = hankenGrotesk,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (call.videoCall) "Video" else "Audio",
                                    fontSize = 11.sp,
                                    fontFamily = hankenGrotesk,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        if (call.groupCall) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    "Group",
                                    fontSize = 11.sp,
                                    fontFamily = hankenGrotesk,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatTimestamp(call.timestamp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = hankenGrotesk,
                    fontSize = 12.sp
                )
            }

            androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Call Type",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = hankenGrotesk
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (call.videoCall) "Video" else "Audio",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = hankenGrotesk,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Duration",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = hankenGrotesk
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (call.duration > 0 && call.status.lowercase() == "completed") {
                                    formatDuration(call.duration)
                                } else "0s",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = hankenGrotesk,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (call.groupCall) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Participants",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = hankenGrotesk
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = displayNames.ifEmpty { "Unknown" },
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = hankenGrotesk,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0s"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return buildString {
        if (h > 0) append("${h}h ")
        if (m > 0 || h > 0) append("${m}m ")
        append("${s}s")
    }.trim()
}
