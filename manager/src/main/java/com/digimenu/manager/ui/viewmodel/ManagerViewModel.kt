package com.digimenu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digimenu.core.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManagerViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    val loggedIn: StateFlow<Boolean> = auth.authState()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, auth.currentUser != null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun login(email: String, password: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            auth.login(email, password)
                .onFailure { _error.value = "Login failed: ${it.message}" }
            _busy.value = false
        }
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }

    fun clearError() {
        _error.value = null
    }
}
