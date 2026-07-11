package com.gitlab.mudlej.MjPdfReader.ui.main

sealed class LoadState {
    object Idle : LoadState()
    object Loading : LoadState()
    object PasswordRequired : LoadState()
    data class Loaded(val pageCount: Int) : LoadState()
    data class Failed(val reason: Throwable) : LoadState()
}
