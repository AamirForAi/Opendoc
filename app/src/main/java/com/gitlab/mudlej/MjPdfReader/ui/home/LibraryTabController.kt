// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.content.Context
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import kotlinx.coroutines.CoroutineScope

class LibraryTabController(
    private val context: Context,
    private val pref: Preferences,
    coverCache: CoverCache,
    scope: CoroutineScope,
    functions: HomeItemFunctions,
    selection: () -> Set<String>,
    private val libraryController: HomeLibraryController,
    private val libraryScanner: LibraryScanner,
    private val hasFullAccess: () -> Boolean,
    onGrantAccessClicked: () -> Unit,
    private val showScanSetup: () -> Boolean,
    onScanSetupClicked: () -> Unit,
    onFilterChanged: () -> Unit,
) {

    val libraryAdapter = LibraryAdapter(coverCache, scope, functions, pref, selection)

    private val sectionsAdapter = HomeSectionsAdapter(
        coverCache,
        scope,
        functions,
        onGrantAccessClicked = onGrantAccessClicked,
        onScanSetupClicked = onScanSetupClicked,
        selectedFilter = ::currentFilter,
        onChipSelected = { filter ->
            pref.setListFilter(filter)
            onFilterChanged()
        },
    )

    private var spanCount = 2
    private var gridLayoutManager: GridLayoutManager? = null

    fun attach(recyclerView: RecyclerView) {
        spanCount = computeSpanCount()
        libraryAdapter.viewMode = pref.getHomeViewMode()
        libraryAdapter.progressStyle = pref.getHomeProgressStyle()
        libraryAdapter.coverWidthPx = context.resources.displayMetrics.widthPixels / spanCount

        val layoutManager = GridLayoutManager(context, spanCount)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (position < sectionsAdapter.itemCount
                    || libraryAdapter.viewMode == HomeViewMode.LIST
                ) {
                    spanCount
                } else {
                    1
                }
            }
        }
        gridLayoutManager = layoutManager

        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = ConcatAdapter(sectionsAdapter, libraryAdapter)
    }

    fun currentFilter(): ListFilter {
        val stored = pref.getListFilter()
        return if (stored == ListFilter.RECENT || stored == ListFilter.FAVORITE) {
            ListFilter.ALL
        } else {
            stored
        }
    }

    fun render(allItems: List<HomeItem>) {
        val filter = currentFilter()
        val gridItems = if (filter == ListFilter.ALL) {
            libraryScanner.refresh()
            libraryController.sort(
                libraryController.mergeWithScan(allItems, libraryScanner.libraryEntries())
            )
        } else {
            libraryController.filterByChip(allItems, filter)
        }.filter { !it.hidden }
        libraryAdapter.submitList(gridItems)

        val scanIndex = libraryScanner.index.value
        val scanningAll = filter == ListFilter.ALL && scanIndex.scanning
        sectionsAdapter.submitList(buildList {
            if (!hasFullAccess()) {
                add(HomeSection.PermissionCard())
            } else if (showScanSetup()) {
                add(HomeSection.ScanSetupCard)
            }
            add(HomeSection.Chips)
            if (scanningAll) {
                add(HomeSection.ScanProgressRow(scanIndex.entries.size))
            }
            if (gridItems.isEmpty() && !scanningAll && !showScanSetup()) {
                add(emptyStateFor(filter))
            }
        })
    }

    fun applyViewMode() {
        libraryAdapter.viewMode = pref.getHomeViewMode()
        libraryAdapter.notifyDataSetChanged()
    }

    fun applyProgressStyle() {
        val style = pref.getHomeProgressStyle()
        if (libraryAdapter.progressStyle != style) {
            libraryAdapter.progressStyle = style
            libraryAdapter.notifyDataSetChanged()
        }
    }

    fun applyTitleStyle() {
        libraryAdapter.applyTitleStyle()
    }

    fun applyGridSize() {
        spanCount = computeSpanCount()
        libraryAdapter.coverWidthPx = context.resources.displayMetrics.widthPixels / spanCount
        gridLayoutManager?.spanCount = spanCount
        libraryAdapter.notifyDataSetChanged()
    }

    fun currentGridItems(): List<HomeItem> = libraryAdapter.currentList

    fun notifySelectionChanged() = libraryAdapter.notifySelectionChanged()

    fun onCoversChanged() {
        sectionsAdapter.rebindCovers()
        libraryAdapter.notifyDataSetChanged()
    }

    private fun computeSpanCount(): Int {
        val screenWidthDp = context.resources.configuration.screenWidthDp
        return (screenWidthDp / pref.getHomeGridSize().targetCellDp).coerceAtLeast(2)
    }

    private fun emptyStateFor(filter: ListFilter): HomeSection.EmptyState {
        return when (filter) {
            ListFilter.ALL -> HomeSection.EmptyState(
                R.string.home_empty_all_title, R.string.home_empty_all_message
            )
            else -> HomeSection.EmptyState(
                R.string.home_empty_status_title, R.string.home_empty_status_message
            )
        }
    }
}
