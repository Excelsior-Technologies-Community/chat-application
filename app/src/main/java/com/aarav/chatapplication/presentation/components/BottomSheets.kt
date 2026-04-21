package com.aarav.chatapplication.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aarav.chatapplication.R
import com.aarav.chatapplication.domain.model.User
import com.aarav.chatapplication.ui.theme.hankenGrotesk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        modifier = Modifier.wrapContentHeight(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        dragHandle = {
            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(
                modifier = Modifier.width(80.dp),
                thickness = 4.dp,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            title?.let {

                Text(
                    it,
                    fontFamily = hankenGrotesk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )


                Spacer(modifier = Modifier.height(12.dp))
            }

            content()
        }
    }
}

@Composable
fun CreateChatModalSheet(
    userList: List<User>,
    onDismiss: () -> Unit,
    onClick: (String) -> Unit
) {
    LazyColumn() {
        items(userList) { user ->
            CreateChatUserCard(user, onClick, onDismiss)
        }
    }
}

@Composable
fun CreateChatUserCard(
    user: User,
    onClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        onClick = {
            user.uid?.let {
                onClick(it)
                onDismiss()
            }
        },
        shape = RoundedCornerShape (24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
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
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Image(
                    painter = painterResource(R.drawable.user),
                    contentDescription = "avatar",
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.inverseSurface),
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
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun AddParticipantsSheet(
    userList: List<User>,
    onDismiss: () -> Unit,
    onAddClick: (List<String>) -> Unit
) {

    val selectedUsers = remember { mutableStateListOf<String>() }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        LazyColumn(
            modifier = Modifier
        ) {
            items(userList) { user ->

                val isSelected = selectedUsers.contains(user.uid)

                AddParticipantUserCard(
                    user = user,
                    isSelected = isSelected,
                    onToggle = {
                        user.uid?.let { id ->
                            if (selectedUsers.contains(id)) {
                                selectedUsers.remove(id)
                            } else {
                                selectedUsers.add(id)
                            }
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                onAddClick(selectedUsers.toList())
                selectedUsers.clear()
                onDismiss()
            },
            enabled = selectedUsers.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text("Add (${selectedUsers.size})")
        }
    }
}


@Composable
fun AddParticipantUserCard(
    user: User,
    isSelected: Boolean,
    onToggle: () -> Unit
) {

    Card(
        onClick = onToggle,
        shape = RoundedCornerShape(24.dp),
        border = if (isSelected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.secondaryContainer
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
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Image(
                    painter = painterResource(R.drawable.user),
                    contentDescription = "avatar",
                    modifier = Modifier
                        .size(36.dp)
                        .padding(8.dp)
                )
            }

            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = user.name ?: "",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = user.phoneNumber
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