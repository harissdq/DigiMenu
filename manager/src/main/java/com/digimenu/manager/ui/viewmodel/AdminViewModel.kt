package com.digimenu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digimenu.core.data.AuthRepository
import com.digimenu.core.data.RestaurantAdminRepository
import com.digimenu.core.data.RestaurantSession
import com.digimenu.core.firebase.FirebaseRefs
import com.digimenu.core.model.RestaurantInfo
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
class AdminViewModel @Inject constructor(
    private val adminRepository: RestaurantAdminRepository,
    private val auth: AuthRepository,
    private val session: RestaurantSession,
) : ViewModel() {

    val isAdmin: StateFlow<Boolean> = auth.authState()
        .map { it?.email?.equals(FirebaseRefs.ADMIN_EMAIL, ignoreCase = true) == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, auth.isAdmin())

    private val _restaurants = MutableStateFlow<List<RestaurantInfo>>(emptyList())
    val restaurants: StateFlow<List<RestaurantInfo>> = _restaurants.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (!auth.isAdmin()) return
        viewModelScope.launch {
            runCatching { adminRepository.listRestaurants() }
                .onSuccess { _restaurants.value = it }
                .onFailure { _message.value = "Could not load restaurants: ${it.message}" }
        }
    }

    fun createRestaurant(
        name: String,
        tables: String,
        managerEmail: String,
        managerPassword: String,
    ) {
        if (_busy.value) return
        val tableLabels = tables.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            runCatching {
                adminRepository.createRestaurant(
                    name = name,
                    tableLabels = tableLabels,
                    managerEmail = managerEmail,
                    managerPassword = managerPassword,
                )
            }.onSuccess { info ->
                _message.value = if (managerEmail.isBlank()) {
                    "Created ${info.name} — you are now its manager."
                } else {
                    "Created ${info.name} and linked a new manager to it."
                }
                if (managerEmail.isBlank()) {
                    runCatching { session.refresh() }
                }
                load()
            }.onFailure {
                _message.value = "Create failed: ${it.message}"
            }
            _busy.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
