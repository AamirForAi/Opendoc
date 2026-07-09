package com.gitlab.mudlej.MjPdfReader.ui.home

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.enums.HomeGridSize
import com.gitlab.mudlej.MjPdfReader.enums.HomeSortOrder
import com.gitlab.mudlej.MjPdfReader.enums.HomeViewMode
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.search.SearchBar

class HomeMenuController(
    private val activity: AppCompatActivity,
    private val searchBar: SearchBar,
    private val pref: Preferences,
    private val onViewModeChanged: () -> Unit,
    private val onGridSizeChanged: () -> Unit,
    private val onSortChanged: () -> Unit,
) {

    fun setup() {
        searchBar.inflateMenu(R.menu.home_search_bar)
        searchBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.viewModeOption -> toggleViewMode()
                R.id.gridSizeOption -> showGridSizeDialog()
                R.id.sortOption -> showSortDialog()
                R.id.settingsOption -> activity.startActivity(
                    Intent(activity, SettingsActivity::class.java)
                )
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        applyViewModeMenuItem()
    }

    private fun toggleViewMode() {
        val newMode = if (pref.getHomeViewMode() == HomeViewMode.GRID) {
            HomeViewMode.LIST
        } else {
            HomeViewMode.GRID
        }
        pref.setHomeViewMode(newMode)
        applyViewModeMenuItem()
        onViewModeChanged()
    }

    private fun applyViewModeMenuItem() {
        val menuItem = searchBar.menu.findItem(R.id.viewModeOption) ?: return
        if (pref.getHomeViewMode() == HomeViewMode.GRID) {
            menuItem.setIcon(R.drawable.ic_view_list)
            menuItem.setTitle(R.string.home_view_list)
        } else {
            menuItem.setIcon(R.drawable.ic_grid_view)
            menuItem.setTitle(R.string.home_view_grid)
        }
    }

    private fun showGridSizeDialog() {
        val labels = arrayOf(
            activity.getString(R.string.home_grid_size_small),
            activity.getString(R.string.home_grid_size_medium),
            activity.getString(R.string.home_grid_size_large),
        )
        val current = pref.getHomeGridSize().ordinal
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.home_grid_size)
            .setSingleChoiceItems(labels, current) { dialog, index ->
                pref.setHomeGridSize(HomeGridSize.entries[index])
                onGridSizeChanged()
                dialog.dismiss()
            }
            .show()
    }

    private fun showSortDialog() {
        val labels = arrayOf(
            activity.getString(R.string.home_sort_last_opened),
            activity.getString(R.string.home_sort_name),
        )
        val current = pref.getHomeSort().ordinal
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.home_sort)
            .setSingleChoiceItems(labels, current) { dialog, index ->
                pref.setHomeSort(HomeSortOrder.entries[index])
                onSortChanged()
                dialog.dismiss()
            }
            .show()
    }
}
