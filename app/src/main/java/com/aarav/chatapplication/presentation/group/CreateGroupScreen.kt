package com.aarav.chatapplication.presentation.group

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aarav.chatapplication.R
import com.aarav.chatapplication.domain.model.User
import com.aarav.chatapplication.presentation.components.MyAlertDialog
import com.aarav.chatapplication.ui.theme.hankenGrotesk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    userId: String,
    onGroupCreated: (groupId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: CreateGroupViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.createdGroupId) {
        uiState.createdGroupId?.let { groupId ->
            onGroupCreated(groupId)
        }
    }

    MyAlertDialog(
        shouldShowDialog = uiState.error != null,
        onDismissRequest = { viewModel.clearError() },
        title = "Error",
        message = uiState.error ?: "",
        confirmButtonText = "OK"
    ) {
        viewModel.clearError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "New Group",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        fontFamily = hankenGrotesk,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = "back",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.selectedUserIds.isNotEmpty() && uiState.groupName.isNotBlank()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.createGroup(userId) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.create_chat),
                        contentDescription = "create",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (uiState.isCreating) "Creating..." else "Create Group",
                        fontFamily = hankenGrotesk,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TextField(
                value = uiState.groupName,
                onValueChange = { viewModel.updateGroupName(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                placeholder = {
                    Text(
                        "Enter group name",
                        fontFamily = hankenGrotesk,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                },
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            if (uiState.selectedUserIds.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        "${uiState.selectedUserIds.size} member${if (uiState.selectedUserIds.size > 1) "s" else ""} selected",
                        fontFamily = hankenGrotesk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Select Members",
                fontFamily = hankenGrotesk,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn {
                items(uiState.userList) { user ->
                    SelectableUserCard(
                        user = user,
                        isSelected = uiState.selectedUserIds.contains(user.uid),
                        onToggle = { user.uid?.let { viewModel.toggleUserSelection(it) } }
                    )
                }
            }
        }
    }
}

@Composable
fun SelectableUserCard(
    user: User,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainer,
        label = "cardColor"
    )

    Card(
        onClick = onToggle,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp))
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
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Image(
                    painter = painterResource(R.drawable.user),
                    contentDescription = "avatar",
                    colorFilter = ColorFilter.tint(
                        if (isSelected)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.inverseSurface
                    ),
                    modifier = Modifier
                        .size(36.dp)
                        .padding(8.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    user.name ?: "",
                    fontFamily = hankenGrotesk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    user.phoneNumber,
                    fontFamily = hankenGrotesk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}
