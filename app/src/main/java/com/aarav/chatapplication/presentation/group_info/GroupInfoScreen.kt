package com.aarav.chatapplication.presentation.group_info

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aarav.chatapplication.R
import com.aarav.chatapplication.domain.model.User
import com.aarav.chatapplication.presentation.components.AddParticipantsSheet
import com.aarav.chatapplication.presentation.components.CustomBottomSheet
import com.aarav.chatapplication.presentation.components.MyAlertDialog
import com.aarav.chatapplication.ui.theme.hankenGrotesk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    groupId: String,
    currentUserId: String,
    onBack: () -> Unit,
    viewModel: GroupInfoViewModel,
    onNavigateToChatList: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddMemberSheet by remember { mutableStateOf(false) }
    var showLeaveConfirmation by remember { mutableStateOf(false) }

    val addMemberSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.observeGroup(groupId)
    }

    LaunchedEffect(uiState.hasLeftGroup) {
        if (uiState.hasLeftGroup) {
            viewModel.clearLeaveState()
            onNavigateToChatList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Group Info",
                        fontFamily = hankenGrotesk,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            "back",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            GroupHeader(
                groupName = uiState.group?.name ?: "",
                memberCount = uiState.members.filter { it.isActive }.size,
                onRenameClick = { showRenameDialog = true },
                isAdmin = uiState.group?.createdBy == currentUserId
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            if (uiState.group?.members?.get(currentUserId) == true) {
                InfoActionItem(
                    icon = R.drawable.add_user,
                    text = "Add Participants",
                    onClick = { showAddMemberSheet = true }
                )
                InfoActionItem(
                    icon = R.drawable.log_out,
                    text = "Leave Group",
                    textColor = MaterialTheme.colorScheme.error,
                    onClick = { showLeaveConfirmation = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Participants (${uiState.members.size})",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontFamily = hankenGrotesk,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.members) { memberInfo ->
                    MemberItem(
                        member = memberInfo.user,
                        isActive = memberInfo.isActive,
                        isCreator = uiState.group?.createdBy == memberInfo.user.uid,
                        canRemove = uiState.group?.createdBy == currentUserId && memberInfo.user.uid != currentUserId,
                        onRemove = {
                            memberInfo.user.uid?.let {
                                viewModel.removeMember(
                                    groupId,
                                    it
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameDialog(
            currentName = uiState.group?.name ?: "",
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                viewModel.updateGroupName(groupId, newName)
                showRenameDialog = false
            }
        )
    }

    if (showAddMemberSheet) {
        CustomBottomSheet(
            sheetState = addMemberSheetState,
            onDismiss = { showAddMemberSheet = false },
            title = "Add Participants"
        ) {
            AddParticipantsSheet(
                userList = uiState.availableUsers,
                onDismiss = { showAddMemberSheet = false },
                onAddClick = { selectedIds ->
                    viewModel.addMembers(groupId, selectedIds)
                    showAddMemberSheet = false
                }
            )
        }
    }

    MyAlertDialog(
        shouldShowDialog = showLeaveConfirmation,
        onDismissRequest = { showLeaveConfirmation = false },
        title = "Leave Group",
        message = "Are you sure you want to leave this group? You will still be able to see the chat history.",
        confirmButtonText = "Leave",
        onConfirmClick = {
            viewModel.leaveGroup(groupId, currentUserId)
            showLeaveConfirmation = false
        }
    )
}

@Composable
fun GroupHeader(
    groupName: String,
    memberCount: Int,
    onRenameClick: () -> Unit,
    isAdmin: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = groupName.take(1).uppercase(),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = hankenGrotesk,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = groupName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = hankenGrotesk,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isAdmin) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            onRenameClick()
                        }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.create_chat),
                        contentDescription = "rename",
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Text(
            text = "$memberCount active members",
            fontSize = 14.sp,
            fontFamily = hankenGrotesk,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun InfoActionItem(
    icon: Int,
    text: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (textColor == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.primary else textColor
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            fontFamily = hankenGrotesk,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
fun MemberItem(
    member: User,
    isActive: Boolean,
    isCreator: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = member.name?.take(1)?.uppercase() ?: "?",
                    fontFamily = hankenGrotesk,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.name ?: "Unknown",
                    fontFamily = hankenGrotesk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
                if (isCreator) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "Admin",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontFamily = hankenGrotesk,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Text(
                text = if (isActive) member.phoneNumber else "Removed/Left",
                fontFamily = hankenGrotesk,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (canRemove && isActive) {
            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(R.drawable.remove),
                    contentDescription = "remove",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Group", fontFamily = hankenGrotesk, fontWeight = FontWeight.Bold) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
