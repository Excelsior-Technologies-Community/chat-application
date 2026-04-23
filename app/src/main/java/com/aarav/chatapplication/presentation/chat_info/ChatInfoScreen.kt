package com.aarav.chatapplication.presentation.chat_info

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aarav.chatapplication.R
import com.aarav.chatapplication.presentation.group_info.InfoActionItem
import com.aarav.chatapplication.ui.theme.hankenGrotesk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(
    userId: String,
    onBack: () -> Unit,
    viewModel: ChatInfoViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) {
        viewModel.getUser(userId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact Info", fontFamily = hankenGrotesk, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.arrow_back), "back", modifier = Modifier.size(18.dp))
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = uiState.user?.name?.take(1)?.uppercase() ?: "?",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = hankenGrotesk,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = uiState.user?.name ?: "Unknown",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = hankenGrotesk,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = uiState.user?.phoneNumber ?: "",
                    fontSize = 16.sp,
                    fontFamily = hankenGrotesk,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            InfoActionItem(
                icon = R.drawable.phone,
                text = "Mute Notifications",
                onClick = {  }
            )

            InfoActionItem(
                icon = R.drawable.log_out,
                text = "Block ${uiState.user?.name ?: "Contact"}",
                textColor = MaterialTheme.colorScheme.error,
                onClick = {  }
            )
        }
    }
}