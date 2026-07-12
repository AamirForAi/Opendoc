// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import com.gitlab.mudlej.MjPdfReader.R

enum class SelectionContext { RECENT, LIBRARY, FOLDERS, SEARCH }

class HomeSelectionController(
    private val activity: AppCompatActivity,
    private val currentItems: () -> List<HomeItem>,
    private val currentContext: () -> SelectionContext,
    private val onSelectionChanged: () -> Unit,
    private val onStatusBatch: (List<HomeItem>) -> Unit,
    private val onRemoveRecentBatch: (List<HomeItem>) -> Unit,
    private val onHideBatch: (List<HomeItem>) -> Unit,
    private val onDeleteBatch: (List<HomeItem>) -> Unit,
) {
    private var actionMode: ActionMode? = null
    private var context = SelectionContext.LIBRARY

    val selectedHashes = mutableSetOf<String>()

    val active: Boolean
        get() = actionMode != null

    fun begin(item: HomeItem): Boolean {
        if (active) {
            toggle(item)
            return true
        }
        selectedHashes.add(item.hash)
        context = currentContext()
        actionMode = activity.startSupportActionMode(callback)
        updateTitle()
        onSelectionChanged()
        return true
    }

    fun toggle(item: HomeItem) {
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
            menu.findItem(R.id.removeRecentBatchOption).isVisible = context == SelectionContext.RECENT
            menu.findItem(R.id.hideBatchOption).isVisible = context == SelectionContext.LIBRARY
            menu.findItem(R.id.deleteBatchOption).isVisible = context == SelectionContext.FOLDERS
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
                R.id.removeRecentBatchOption -> onRemoveRecentBatch(items)
                R.id.hideBatchOption -> onHideBatch(items)
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
