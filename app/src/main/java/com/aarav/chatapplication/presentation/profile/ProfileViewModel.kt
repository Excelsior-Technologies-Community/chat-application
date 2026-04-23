package com.aarav.chatapplication.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarav.chatapplication.domain.model.User
import com.aarav.chatapplication.domain.repository.AuthRepository
import com.aarav.chatapplication.domain.repository.UserRepository
import com.aarav.chatapplication.utils.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user = _user.asStateFlow()

    init {
        loadCurrentUser()
    }

    private fun loadCurrentUser() {
        val result = userRepository.getCurrentUser()
        if (result is Result.Success && result.data != null) {
            viewModelScope.launch {
                userRepository.findUserByUserId(result.data).collect { user ->
                    _user.value = user
                }
            }
        }
    }

    fun logout() {
        authRepository.logout()
    }
}