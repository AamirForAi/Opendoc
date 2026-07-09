package com.gitlab.mudlej.MjPdfReader.ui.home

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import com.gitlab.mudlej.MjPdfReader.R

class HomeSelectionController(
    private val activity: AppCompatActivity,
    private val currentItems: () -> List<HomeItem>,
    private val onSelectionChanged: () -> Unit,
    private val onStatusBatch: (List<HomeItem>) -> Unit,
    private val onDeleteBatch: (List<HomeItem>) -> Unit,
) {
    private var actionMode: ActionMode? = null

    val selectedHashes = mutableSetOf<String>()

    val active: Boolean
        get() = actionMode != null

    fun begin(item: HomeItem): Boolean {
        if (item.isScanOnly) {
            return false
        }
        if (active) {
            toggle(item)
            return true
        }
        selectedHashes.add(item.hash)
        actionMode = activity.startSupportActionMode(callback)
        updateTitle()
        onSelectionChanged()
        return true
    }

    fun toggle(item: HomeItem) {
        if (item.isScanOnly) {
            return
        }
        if (!selectedHashes.remove(item.hash)) {
            selectedHashes.add(item.hash)
        }
        if (selectedHashes.isEmpty()) {
            finish()
            return
        }
        updateTitle()
        onSelectionChanged()
    }

    fun finish() {
        actionMode?.finish()
    }

    private fun updateTitle() {
        actionMode?.title = activity.getString(R.string.home_selected_count, selectedHashes.size)
    }

    private fun selectedItems(): List<HomeItem> {
        return currentItems().filter { it.hash in selectedHashes }
    }

    private val callback = object : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.home_action_mode, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val items = selectedItems()
            if (items.isEmpty()) {
                return true
            }
            when (item.itemId) {
                R.id.statusBatchOption -> onStatusBatch(items)
                R.id.deleteBatchOption -> onDeleteBatch(items)
                else -> return false
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            selectedHashes.clear()
            actionMode = null
            onSelectionChanged()
        }
    }
}
