package com.aarav.chatapplication.presentation.auth

import android.app.Activity
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.domain.model.User
import com.aarav.chatapplication.domain.repository.AuthRepository
import com.aarav.chatapplication.domain.repository.UserRepository
import com.aarav.chatapplication.utils.PhoneAuthState
import com.aarav.chatapplication.utils.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isCodeSent: Boolean = false,
    val verificationId: String? = null,
    val isVerified: Boolean = false,
    val userId: String? = null,
    val error: String? = null,
    val showErrorDialog: Boolean = false,
    val phone: String = "",
    val name: String = "",
    val nameError: String? = null,
    val phoneError: String? = null,
    val isInputValid: Boolean = false,
    val otpValid: Boolean = false,
    val errorType: AuthError? = null
)

enum class AuthError {
    invalidInput, invalidOTP
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    val authRepository: AuthRepository,
    val userRepository: UserRepository
) : ViewModel() {
    private var _uiState: MutableStateFlow<AuthUiState> = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    var hasInteractedWithName = false
    var hasInteractedWithPhone = false

    fun updateName(name: String) {
        hasInteractedWithName = true
        _uiState.update {
            it.copy(
                name = name
            )
        }
        validateInput()
    }

    fun updatePhone(phone: String) {
        hasInteractedWithPhone = true
        _uiState.update {
            it.copy(
                phone = phone
            )
        }
        validateInput()
    }

    fun validateInput() {

        val name = _uiState.value.name
        val phone = _uiState.value.phone

        val isNameValid = name.length >= 2
        val isPhoneValid = Patterns.PHONE.matcher(phone).matches() && phone.length == 10

        _uiState.update {
            it.copy(
                isInputValid = isNameValid && isPhoneValid,
                nameError = if (!isNameValid && hasInteractedWithName) "Name should contain at least 2 characters" else null,
                phoneError = if (!isPhoneValid && hasInteractedWithPhone) "Enter valid 10 digit phone number" else null
            )
        }
    }

    fun sendOtp(phone: String, activity: Activity) {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {

            if (phone.length == 13) {
                _uiState.update {
                    it.copy(
                        isInputValid = true,
                        isLoading = true,
                        error = null
                    )
                }

                authRepository.sendOtp(phone, activity)
                    .collect { result ->
                        when (result) {
                            is PhoneAuthState.CodeSent -> {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isCodeSent = true,
                                        verificationId = result.verificationId
                                    )
                                }
                            }

                            is PhoneAuthState.Verified -> {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isVerified = true,
                                        userId = result.uid
                                    )
                                }
                            }

                            is PhoneAuthState.Error -> {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        error = result.message,
                                        showErrorDialog = true,
                                    )
                                }
                            }
                        }
                    }
            } else {
                _uiState.update {
                    it.copy(
                        showErrorDialog = true,
                        errorType = AuthError.invalidInput,
                        error = "Please enter a valid 10 digit phone number"
                    )
                }
            }
        }
    }

    fun verifyOtp(verificationId: String, otp: String) {
        viewModelScope.launch {

            if (otp.length == 6) {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        error = null
                    )
                }

                when (val result = authRepository.verifyOtp(verificationId, otp)) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isVerified = true,
                            )
                        }
                    }

                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                error = result.message,
                                showErrorDialog = true,
                                isLoading = false,
                                errorType = AuthError.invalidOTP
                            )
                        }
                    }
                }
            } else {
                _uiState.update {
                    it.copy(
                        showErrorDialog = true,
                        errorType = AuthError.invalidOTP,
                        error = "Enter a valid OTP"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(
                showErrorDialog = false,
                error = null
            )
        }
    }

    fun storeUserData(user: User) {
        val user = User(
            _uiState.value.userId,
            _uiState.value.phone,
            _uiState.value.name,
        )

        viewModelScope.launch {
            userRepository.storeUserInfo(user)
        }
    }

    fun logout() {
        authRepository.logout()
    }
}