package com.aarav.chatapplication.presentation.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.aarav.chatapplication.presentation.navigation.BottomNavigation
import com.aarav.chatapplication.presentation.navigation.NavRoute
import com.aarav.chatapplication.ui.theme.hankenGrotesk
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsState()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        fontFamily = hankenGrotesk,
                        color = Color(0xFF575459)
                    )
                }
            )
        },
        bottomBar = { BottomNavigation(navController) }
    ) { paddingValues ->
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(28.dp))

                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user?.name?.take(1)?.uppercase() ?: "?",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontFamily = hankenGrotesk
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = user?.name ?: "",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = hankenGrotesk,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(28.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProfileInfoCard(
                        iconRes = R.drawable.phone,
                        label = "Phone Number",
                        value = ("+91 " + user?.phoneNumber?.ifEmpty { "" })
                    )

                    ProfileInfoCard(
                        iconRes = R.drawable.chat_circle,
                        label = "Member Since",
                        value = user?.let { formatJoinDate(it.createdAt) } ?: "—"
                    )
                }

                Spacer(Modifier.height(24.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.logout()
                        navController.navigate(NavRoute.Auth.path) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.log_out),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Log Out",
                        fontFamily = hankenGrotesk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ProfileInfoCard(
    iconRes: Int,
    label: String,
    value: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontFamily = hankenGrotesk,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = hankenGrotesk,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun formatJoinDate(timestamp: Long): String {
    return try {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    } catch (e: Exception) {
        ""
    }
}