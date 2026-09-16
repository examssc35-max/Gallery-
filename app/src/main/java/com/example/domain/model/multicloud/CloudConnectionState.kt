package com.example.domain.model.multicloud

sealed class CloudConnectionState {
    data object NotConnected : CloudConnectionState()
    data object Connecting : CloudConnectionState()
    data class Connected(
        val accountName: String? = null,
        val accountEmail: String? = null,
        val serviceInfo: String? = null
    ) : CloudConnectionState()
    data object Refreshing : CloudConnectionState()
    data object Offline : CloudConnectionState()
    data class AuthRequired(val message: String = "Authentication required") : CloudConnectionState()
    data class PermissionRequired(val message: String = "Missing necessary permissions") : CloudConnectionState()
    data class Error(val message: String) : CloudConnectionState()

    val isConnected: Boolean
        get() = this is Connected || this is Refreshing
}
