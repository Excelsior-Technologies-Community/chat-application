package com.aarav.chatapplication.presentation.auth

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aarav.chatapplication.R
import com.aarav.chatapplication.presentation.components.MyAlertDialog
import com.aarav.chatapplication.ui.theme.hankenGrotesk

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuthScreen(
    navigateToHome: () -> Unit,
    viewModel: AuthViewModel
) {

    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity

    var show by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isCodeSent) {
        if (uiState.isCodeSent) { }
    }

    MyAlertDialog(
        shouldShowDialog = uiState.showErrorDialog,
        onDismissRequest = { viewModel.clearError() },
        title = when (uiState.errorType) {
            AuthError.invalidInput -> "Invalid Input"
            AuthError.invalidOTP -> "Invalid OTP"
            else -> "Error"
        },
        message = uiState.error ?: "",
        confirmButtonText = "Dismiss"
    ) {
        viewModel.clearError()
    }

    if (!uiState.isCodeSent) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "ChatApp",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = hankenGrotesk,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Stay synced with your circle with online chatting",
                fontSize = 20.sp,
                fontFamily = hankenGrotesk,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "We will send you an OTP to verify your phone number. Enter your phone number ",
                fontSize = 16.sp,
                fontFamily = hankenGrotesk,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.phone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                onValueChange = { input ->
                    val digitsOnly = input.filter { it.isDigit() }
                    if (digitsOnly.length <= 10) {
                        viewModel.updatePhone(digitsOnly)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                leadingIcon = {
                    Image(
                        painter = painterResource(R.drawable.phone),
                        contentDescription = "phone",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.size(24.dp)
                    )
                },
                maxLines = 1,
                shape = RoundedCornerShape(14.dp),
                placeholder = {
                    Text("Enter phone number", fontFamily = hankenGrotesk, color = MaterialTheme.colorScheme.onBackground)
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = Color.DarkGray,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                isError = uiState.phoneError != null,
                supportingText = {
                    uiState.phoneError?.let {
                        Text(
                            it,
                            fontFamily = hankenGrotesk,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    show = true
                    val finalPhone = "+91${uiState.phone}"
                    activity?.let {
                        viewModel.sendOtp(finalPhone, it)
                    }
                },
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = Color(0xFFFFC9D2),
                    disabledContentColor = Color.White.copy(alpha = 0.6f)
                )
            ) {
                Text(
                    text = "Next",
                    fontFamily = hankenGrotesk,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    } else {
        OPTScreen(uiState, viewModel, navigateToHome)
    }

    if (uiState.isLoading) {
        Dialog(onDismissRequest = {}, content = {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator()
                }
            }
        })
    }
}