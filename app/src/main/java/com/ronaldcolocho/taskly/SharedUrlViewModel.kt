package com.ronaldcolocho.taskly

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedUrlViewModel : ViewModel() {
    private val _sharedUrl = MutableStateFlow<String?>(null)
    val sharedUrl = _sharedUrl.asStateFlow()

    fun receive(url: String) {
        _sharedUrl.value = url
    }

    fun markHandled(url: String) {
        if (_sharedUrl.value == url) _sharedUrl.value = null
    }
}
