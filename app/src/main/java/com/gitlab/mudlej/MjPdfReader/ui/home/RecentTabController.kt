package com.gitlab.mudlej.MjPdfReader.ui.home

import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.enums.HomeViewMode
import com.gitlab.mudlej.MjPdfReader.manager.thumbnail.CoverCache
import kotlinx.coroutines.CoroutineScope

class RecentTabController(
    coverCache: CoverCache,
    scope: CoroutineScope,
    functions: HomeItemFunctions,
    private val libraryController: HomeLibraryController,
) {

    private val sectionsAdapter = HomeSectionsAdapter(coverCache, scope, functions)
    private val rowsAdapter = LibraryAdapter(coverCache, scope, functions).apply {
        viewMode = HomeViewMode.LIST
    }

    fun attach(recyclerView: RecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
        recyclerView.adapter = ConcatAdapter(sectionsAdapter, rowsAdapter)
    }

    fun render(allItems: List<HomeItem>) {
        val visibleItems = allItems.filter { !it.hidden }
        val heroItems = libraryController.continueReading(visibleItems)
        val rows = visibleItems
            .filter { it.hasBeenOpened }
            .sortedByDescending { it.lastOpened }

        rowsAdapter.submitList(rows)
        sectionsAdapter.submitList(buildList {
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

    fun onCoversChanged() {
        sectionsAdapter.rebindCovers()
        rowsAdapter.notifyDataSetChanged()
    }
}
