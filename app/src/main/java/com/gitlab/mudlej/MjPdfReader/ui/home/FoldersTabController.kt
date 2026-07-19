// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.content.Context
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import kotlinx.coroutines.CoroutineScope

class FoldersTabController(
    private val context: Context,
    private val pref: Preferences,
    coverCache: CoverCache,
    scope: CoroutineScope,
    functions: HomeItemFunctions,
    onGrantAccessClicked: () -> Unit,
    private val hasFullAccess: () -> Boolean,
    private val libraryController: HomeLibraryController,
    private val onNavigationChanged: () -> Unit,
    selection: () -> Set<String> = { emptySet() },
) {

    private val topSections = HomeSectionsAdapter(
        coverCache,
        scope,
        functions,
        onGrantAccessClicked = onGrantAccessClicked,
    )
    private val breadcrumbAdapter = BreadcrumbAdapter { path -> navigateTo(path) }
    private val folderAdapter = FolderAdapter { node -> navigateTo(node.path) }
    private val filesAdapter = LibraryAdapter(coverCache, scope, functions, pref, selection).apply {
        viewMode = HomeViewMode.LIST
        metaStyle = ListMetaStyle.FOLDERS
    }
    private val bottomSections = HomeSectionsAdapter(coverCache, scope, functions)

    private var index: FolderIndex? = null
    private var currentDir: String? = null
    private var lastItems: List<HomeItem> = emptyList()
    private var lastScanning = false
    private var lastEntriesCount = 0

    fun attach(recyclerView: RecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
        recyclerView.adapter = ConcatAdapter(
            topSections, breadcrumbAdapter, folderAdapter, filesAdapter, bottomSections
        )
    }

    fun render(allItems: List<HomeItem>, entries: List<ScannedPdfEntry>, scanning: Boolean) {
        index = FolderIndex(entries, context.getString(R.string.home_folder_storage))
        lastItems = allItems
        lastScanning = scanning
        lastEntriesCount = entries.size
        renderCurrent()
    }

    fun onModeChanged() {
        currentDir = null
        renderCurrent()
    }

    fun canGoBack(): Boolean = currentDir != null

    fun currentItems(): List<HomeItem> = filesAdapter.currentList

    fun applyTitleStyle() = filesAdapter.applyTitleStyle()

    fun notifySelectionChanged() = filesAdapter.notifySelectionChanged()

    fun goBack(): Boolean {
        val dir = currentDir ?: return false
        currentDir = if (pref.getHomeFolderFlat()) {
            null
        } else {
            index?.parentOf(dir)
        }
        renderCurrent()
        return true
    }

    fun onCoversChanged() {
        filesAdapter.notifyDataSetChanged()
        topSections.rebindCovers()
    }

    private fun navigateTo(path: String?) {
        currentDir = path
        renderCurrent()
    }

    private fun renderCurrent() {
        val idx = index ?: return
        val flat = pref.getHomeFolderFlat()
        val dir = currentDir

        val folders: List<FolderNode>
        val files: List<ScannedPdfEntry>
        val crumbs: List<Crumb>

        if (flat) {
            if (dir == null) {
                folders = idx.flatFolders()
                files = emptyList()
                crumbs = emptyList()
            } else {
                folders = emptyList()
                files = idx.filesIn(dir)
                crumbs = idx.crumbsFor(dir)
            }
        } else {
            if (dir == null) {
                if (idx.roots.size > 1) {
                    folders = idx.rootFolders()
                    files = emptyList()
                } else {
                    val root = idx.roots.firstOrNull()
                    folders = root?.let { idx.foldersIn(it) }.orEmpty()
                    files = root?.let { idx.filesIn(it) }.orEmpty()
                }
                crumbs = emptyList()
            } else {
                folders = idx.foldersIn(dir)
                files = idx.filesIn(dir)
                crumbs = idx.crumbsFor(dir)
            }
        }

        breadcrumbAdapter.submit(crumbs)
        folderAdapter.submitList(folders)
        filesAdapter.submitList(libraryController.mergeWithScan(lastItems, files))

        topSections.submitList(buildList {
            if (!hasFullAccess()) {
                add(HomeSection.PermissionCard())
            }
            if (lastScanning) {
                add(HomeSection.ScanProgressRow(lastEntriesCount))
            }
        })
        bottomSections.submitList(buildList {
            if (folders.isEmpty() && files.isEmpty() && !lastScanning) {
                add(
                    HomeSection.EmptyState(
                        R.string.home_empty_all_title, R.string.home_empty_all_message
                    )
                )
            }
        })

        onNavigationChanged()
    }
}
