// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class HomeViewModel(state: SavedStateHandle) : ViewModel() {

    var pendingRelocateHash: String? = null

    init {
        state.setSavedStateProvider(STATE_KEY) { snapshotState() }
        state.get<Bundle>(STATE_KEY)?.let { restoreState(it) }
    }

    private fun snapshotState(): Bundle {
        val out = Bundle()
        out.putString(KEY_RELOCATE_HASH, pendingRelocateHash)
        return out
    }

    private fun restoreState(saved: Bundle) {
        pendingRelocateHash = saved.getString(KEY_RELOCATE_HASH)
    }

    private companion object {
        const val STATE_KEY = "homeViewModelState"
        const val KEY_RELOCATE_HASH = "homePendingRelocateHash"
    }
}
