// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import kotlinx.coroutines.CoroutineScope

class RecentTabController(
    coverCache: CoverCache,
    scope: CoroutineScope,
    functions: HomeItemFunctions,
    pref: Preferences,
    private val libraryController: HomeLibraryController,
    private val hasFullAccess: () -> Boolean,
    onGrantAccessClicked: () -> Unit,
    selection: () -> Set<String> = { emptySet() },
) {

    private val sectionsAdapter = HomeSectionsAdapter(coverCache, scope, functions, onGrantAccessClicked)
    private val rowsAdapter = LibraryAdapter(coverCache, scope, functions, pref, selection).apply {
        viewMode = HomeViewMode.LIST
        metaStyle = ListMetaStyle.RECENT
    }

    fun attach(recyclerView: RecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
        recyclerView.adapter = ConcatAdapter(sectionsAdapter, rowsAdapter)
    }

    fun render(allItems: List<HomeItem>) {
        val visibleItems = allItems.filter { !it.hidden }
        val heroItems = libraryController.continueReading(visibleItems.filter { it.available })
        val rows = visibleItems
            .filter { it.hasBeenOpened }
            .sortedByDescending { it.lastOpened }

        rowsAdapter.submitList(rows)
        sectionsAdapter.submitList(buildList {
            if (!hasFullAccess() && rows.any { !it.available }) {
                add(HomeSection.PermissionCard(R.string.home_recent_permission_message))
            }
            if (heroItems.isNotEmpty()) {
                add(HomeSection.Hero(heroItems))
            }
            if (rows.isEmpty()) {
                add(
                    HomeSection.EmptyState(
                        R.string.home_empty_recent_title, R.string.home_empty_recent_message
                    )
                )
            }
        })
    }

    fun currentItems(): List<HomeItem> = rowsAdapter.currentList

    fun applyTitleStyle() = rowsAdapter.applyTitleStyle()

    fun notifySelectionChanged() = rowsAdapter.notifySelectionChanged()

    fun onCoversChanged() {
        sectionsAdapter.rebindCovers()
        rowsAdapter.notifyDataSetChanged()
    }
}
