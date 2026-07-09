package com.gitlab.mudlej.MjPdfReader.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityHomeBinding
import com.gitlab.mudlej.MjPdfReader.enums.HomeViewMode
import com.gitlab.mudlej.MjPdfReader.enums.ListFilter
import com.gitlab.mudlej.MjPdfReader.enums.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManager
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManagerImpl
import com.gitlab.mudlej.MjPdfReader.manager.permission.PermissionManager
import com.gitlab.mudlej.MjPdfReader.manager.storage.LibraryScanner
import com.gitlab.mudlej.MjPdfReader.manager.thumbnail.CoverCache
import com.gitlab.mudlej.MjPdfReader.repository.AppDatabase
import com.gitlab.mudlej.MjPdfReader.ui.showAppFeaturesDialog
import com.gitlab.mudlej.MjPdfReader.ui.main.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.main.MainIntroActivity
import com.gitlab.mudlej.MjPdfReader.util.PersistedGrantKeeper
import com.gitlab.mudlej.MjPdfReader.util.StringUtil.formatEnumToTitle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity(), HomeItemFunctions {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var pref: Preferences
    private lateinit var databaseManager: DatabaseManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var coverCache: CoverCache
    private lateinit var libraryController: HomeLibraryController
    private lateinit var libraryAdapter: LibraryAdapter
    private lateinit var sectionsAdapter: HomeSectionsAdapter
    private lateinit var searchResultsAdapter: LibraryAdapter
    private lateinit var menuController: HomeMenuController
    private lateinit var gridLayoutManager: GridLayoutManager
    private lateinit var libraryScanner: LibraryScanner
    private lateinit var selectionController: HomeSelectionController
    private lateinit var relocateController: RelocateController

    private var spanCount = 2
    private var allItems: List<HomeItem> = emptyList()

    private val pdfPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            PersistedGrantKeeper.takeReadGrant(this, uri)
            openInReader(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pref = Preferences(PreferenceManager.getDefaultSharedPreferences(this))

        if (pref.getFirstInstall()) {
            pref.setFirstInstall(false)
            pref.setShowFeaturesDialog(true)
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                startActivity(Intent(this, MainIntroActivity::class.java))
            }
        }

        if (pref.getHomeDisabled()) {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
            return
        }

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        databaseManager = DatabaseManagerImpl(AppDatabase.getInstance(applicationContext))
        coverCache = CoverCache.getInstance(applicationContext)
        permissionManager = PermissionManager(this) { onStorageAccessChanged() }
        libraryController = HomeLibraryController(databaseManager, pref)
        libraryScanner = LibraryScanner.getInstance(applicationContext)
        selectionController = HomeSelectionController(
            this,
            currentItems = { libraryAdapter.currentList },
            onSelectionChanged = { libraryAdapter.notifySelectionChanged() },
            onStatusBatch = ::statusBatch,
            onDeleteBatch = ::deleteBatch,
        )
        relocateController = RelocateController(
            this,
            databaseManager,
            libraryScanner,
            lifecycleScope,
            onOpen = ::openInReader,
            onHealed = ::refresh,
        )

        setupRecyclerView()
        setupSearch()
        menuController = HomeMenuController(
            this,
            binding.searchBar,
            pref,
            onViewModeChanged = ::applyViewMode,
            onGridSizeChanged = ::applyGridSize,
            onSortChanged = ::refresh,
        )
        menuController.setup()
        binding.openPdfFab.setOnClickListener { pdfPicker.launch(arrayOf(PDF.FILE_TYPE)) }

        lifecycleScope.launch {
            libraryScanner.index.collect { refresh() }
        }

        showAppFeaturesDialogOnFirstRun()
        handleRelocateIntent(intent)
    }

    private fun showAppFeaturesDialogOnFirstRun() {
        if (pref.getShowFeaturesDialog()) {
            lifecycleScope.launch {
                delay(500)
                if (!isFinishing) {
                    showAppFeaturesDialog(this@HomeActivity)
                }
            }
            pref.setShowFeaturesDialog(false)
        }
    }

    override fun onStart() {
        super.onStart()
        libraryScanner.startObserving()
    }

    override fun onStop() {
        libraryScanner.stopObserving()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRelocateIntent(intent)
    }

    private fun handleRelocateIntent(intent: Intent) {
        val relocateHash = intent.getStringExtra(EXTRA_RELOCATE_HASH) ?: return
        intent.removeExtra(EXTRA_RELOCATE_HASH)
        relocateController.handleMissingFile(relocateHash)
    }

    override fun onResume() {
        super.onResume()
        permissionManager.recheck()
        libraryScanner.refresh()
        refresh()
    }

    private fun setupRecyclerView() {
        libraryAdapter = LibraryAdapter(coverCache, lifecycleScope, this) {
            selectionController.selectedHashes
        }
        sectionsAdapter = HomeSectionsAdapter(
            coverCache,
            lifecycleScope,
            this,
            onGrantAccessClicked = { permissionManager.requestFullAccess() },
            selectedFilter = ::currentFilter,
            onChipSelected = { filter -> onChipSelected(filter) },
        )

        spanCount = computeSpanCount()
        libraryAdapter.viewMode = pref.getHomeViewMode()
        libraryAdapter.coverWidthPx = resources.displayMetrics.widthPixels / spanCount

        gridLayoutManager = GridLayoutManager(this, spanCount)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
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

        binding.homeRecyclerView.layoutManager = gridLayoutManager
        binding.homeRecyclerView.adapter = ConcatAdapter(sectionsAdapter, libraryAdapter)
    }

    private fun setupSearch() {
        binding.searchView.setupWithSearchBar(binding.searchBar)

        searchResultsAdapter = LibraryAdapter(coverCache, lifecycleScope, this)
        searchResultsAdapter.viewMode = HomeViewMode.LIST
        binding.searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.searchResultsRecyclerView.adapter = searchResultsAdapter

        binding.searchView.editText.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString().orEmpty()
            searchResultsAdapter.submitList(libraryController.filterByQuery(allItems, query))
        }
    }

    private fun applyViewMode() {
        libraryAdapter.viewMode = pref.getHomeViewMode()
        libraryAdapter.notifyDataSetChanged()
    }

    private fun applyGridSize() {
        spanCount = computeSpanCount()
        libraryAdapter.coverWidthPx = resources.displayMetrics.widthPixels / spanCount
        gridLayoutManager.spanCount = spanCount
        libraryAdapter.notifyDataSetChanged()
    }

    private fun computeSpanCount(): Int {
        val screenWidthDp = resources.configuration.screenWidthDp
        return (screenWidthDp / pref.getHomeGridSize().targetCellDp).coerceAtLeast(2)
    }

    private fun onChipSelected(filter: ListFilter) {
        pref.setListFilter(filter)
        refresh()
    }

    private fun currentFilter(): ListFilter {
        val stored = pref.getListFilter()
        return if (stored == ListFilter.RECENT || stored == ListFilter.FAVORITE) {
            ListFilter.ALL
        } else {
            stored
        }
    }

    private fun onStorageAccessChanged() {
        libraryAdapter.notifyDataSetChanged()
        searchResultsAdapter.notifyDataSetChanged()
        sectionsAdapter.rebindCovers()
        libraryScanner.refresh(force = true)
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            allItems = libraryController.loadLibrary()
            val filter = currentFilter()
            val gridItems = if (filter == ListFilter.ALL) {
                libraryScanner.refresh()
                libraryController.sort(
                    libraryController.mergeWithScan(allItems, libraryScanner.index.value.entries)
                )
            } else {
                libraryController.filterByChip(allItems, filter)
            }
            libraryAdapter.submitList(gridItems)
            updateSections(allItems, gridItems, filter)
        }
    }

    private fun updateSections(
        allItems: List<HomeItem>,
        gridItems: List<HomeItem>,
        filter: ListFilter,
    ) {
        val heroItems = libraryController.continueReading(allItems)
        val recentItems = libraryController.recents(allItems, excluding = heroItems)

        val sections = buildList {
            if (!permissionManager.hasFullAccess()) {
                add(HomeSection.PermissionCard)
            }
            if (heroItems.isNotEmpty()) {
                add(HomeSection.Hero(heroItems))
            }
            if (recentItems.isNotEmpty()) {
                add(HomeSection.Recents(recentItems))
            }
            val scanIndex = libraryScanner.index.value
            add(HomeSection.Chips)
            if (filter == ListFilter.ALL && scanIndex.scanning) {
                add(HomeSection.ScanProgressRow(scanIndex.entries.size))
            }
            if (gridItems.isEmpty() && !(filter == ListFilter.ALL && scanIndex.scanning)) {
                add(emptyStateFor(filter))
            }
        }
        sectionsAdapter.submitList(sections)
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

    private fun openInReader(uri: Uri, hash: String? = null) {
        Intent(this, MainActivity::class.java).also { intent ->
            intent.data = uri
            intent.putExtra(EXTRA_FROM_HOME, true)
            hash?.let { intent.putExtra(EXTRA_RECORD_HASH, it) }
            startActivity(intent)
        }
    }

    override fun onItemClicked(item: HomeItem) {
        if (selectionController.active) {
            selectionController.toggle(item)
            return
        }
        if (binding.searchView.isShowing) {
            binding.searchView.hide()
        }
        if (isMissingFile(item)) {
            relocateController.handleMissingFile(item.hash)
            return
        }
        openInReader(item.uri, item.hash.takeUnless { item.isScanOnly })
    }

    private fun isMissingFile(item: HomeItem): Boolean {
        if (item.isScanOnly || item.uri.scheme != "file") {
            return false
        }
        val path = item.uri.path ?: return false
        return !File(path).canRead()
    }

    override fun onItemLongClicked(item: HomeItem): Boolean {
        if (binding.searchView.isShowing) {
            return false
        }
        return selectionController.begin(item)
    }

    private fun statusBatch(items: List<HomeItem>) {
        val labels = ReadingStatus.entries
            .map { it.name.formatEnumToTitle() }
            .toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.home_set_status)
            .setItems(labels) { _, index ->
                lifecycleScope.launch {
                    databaseManager.setReadingBatch(
                        items.map { it.hash }, ReadingStatus.entries[index]
                    )
                    selectionController.finish()
                    refresh()
                }
            }
            .show()
    }

    private fun deleteBatch(items: List<HomeItem>) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_dialog_title)
            .setMessage(R.string.delete_dialog_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    databaseManager.removeRecords(items.map { it.hash })
                    items.forEach { coverCache.invalidate(it.hash) }
                    selectionController.finish()
                    refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_FROM_HOME = "fromHome"
        const val EXTRA_RECORD_HASH = "recordHash"
        const val EXTRA_RELOCATE_HASH = "relocateHash"
    }
}
