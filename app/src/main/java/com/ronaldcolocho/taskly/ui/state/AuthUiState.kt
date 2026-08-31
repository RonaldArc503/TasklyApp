package com.ronaldcolocho.taskly.ui.state

sealed interface AuthState {
    object Uninitialized : AuthState
    object LoggedOut : AuthState
    object LoggedIn : AuthState
}
