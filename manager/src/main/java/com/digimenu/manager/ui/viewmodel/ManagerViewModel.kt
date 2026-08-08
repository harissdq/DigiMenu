package com.digimenu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digimenu.core.data.AuthRepository
import com.digimenu.core.data.RestaurantSession
import com.digimenu.core.firebase.FirebaseRefs
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
    private val session: RestaurantSession,
) : ViewModel() {

    val loggedIn: StateFlow<Boolean> = auth.authState()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, auth.currentUser != null)

    /** True for the bootstrap admin account (can create restaurants). */
    val isAdmin: StateFlow<Boolean> = auth.authState()
        .map { it?.email?.equals(FirebaseRefs.ADMIN_EMAIL, ignoreCase = true) == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, auth.isAdmin())

    val restaurantName: StateFlow<String?> = session.restaurantName

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // A signed-in session survives a process restart; re-resolve the tenant
        // so the menu/QR/orders screens work without having to log in again.
        viewModelScope.launch {
            if (auth.currentUser != null) {
                refreshSession()
            }
        }
    }

    fun login(email: String, password: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            auth.login(email, password)
                .onSuccess {
                    refreshSession()
                    if (!auth.isManager()) {
                        auth.logout()
                        _error.value = "This account is not an authorised manager."
                    }
                }
                .onFailure { _error.value = "Login failed: ${it.message}" }
            _busy.value = false
        }
    }

    private fun refreshSession() {
        // Any single failure (e.g. a denied info read) must never crash login.
        runCatching { session.refresh() }
    }

    fun logout() {
        viewModelScope.launch {
            session.clear()
            auth.logout()
        }
    }

    fun clearError() {
        _error.value = null
    }
}
