// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityHomeBinding
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.data.HistoryCleaner
import com.gitlab.mudlej.MjPdfReader.data.HistoryPolicy
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationJournal
import com.gitlab.mudlej.MjPdfReader.data.signature.SignatureStore
import com.gitlab.mudlej.MjPdfReader.core.PermissionManager
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.ui.about.WhatsNewActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.intro.MainIntroActivity
import com.gitlab.mudlej.MjPdfReader.core.io.PersistedGrantKeeper
import com.gitlab.mudlej.MjPdfReader.core.text.StringUtil.formatEnumToTitle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity(), HomeItemFunctions {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var pref: Preferences
    private lateinit var pdfRepository: PdfRepository
    private lateinit var historyPolicy: HistoryPolicy
    private lateinit var historyCleaner: HistoryCleaner
    private lateinit var permissionManager: PermissionManager
    private lateinit var coverCache: CoverCache
    private lateinit var libraryController: HomeLibraryController
    private lateinit var libraryScanner: LibraryScanner
    private lateinit var selectionController: HomeSelectionController
    private lateinit var relocateController: RelocateController
    private lateinit var menuDialog: HomeMenuDialog
    private lateinit var scanSetupDialog: ScanSetupDialog
    private lateinit var recordOptionsDialog: RecordOptionsDialog
    private lateinit var searchResultsAdapter: LibraryAdapter
    private lateinit var recentTab: RecentTabController
    private lateinit var libraryTab: LibraryTabController
    private lateinit var foldersTab: FoldersTabController

    private var allItems: List<HomeItem> = emptyList()

    private val foldersBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            foldersTab.goBack()
        }
    }

    private var pickIncognito = false

    private val scanLocationsPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                libraryScanner.refresh(force = true)
                refresh()
            }
        }

    private val pdfPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val incognito = pickIncognito
        pickIncognito = false
        if (uri != null) {
            if (!incognito) {
                PersistedGrantKeeper.takeReadGrant(this, uri)
            }
            openInReader(uri, incognito = incognito)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pref = Preferences(PreferenceManager.getDefaultSharedPreferences(this))

        if (pref.getFirstInstall()) {
            pref.setFirstInstall(false)
            pref.setShowFeaturesDialog(true)
            startActivity(Intent(this, MainIntroActivity::class.java))
        }

        if (pref.getHomeDisabled()) {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
            return
        }

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pdfRepository = PdfRepository(AppDatabase.getInstance(applicationContext))
        coverCache = CoverCache.getInstance(applicationContext)
        historyPolicy = HistoryPolicy(pref)
        historyCleaner = HistoryCleaner(
            pdfRepository,
            AnnotationJournal(applicationContext),
            SignatureStore(applicationContext),
            coverCache,
        )
        permissionManager = PermissionManager(this) { onStorageAccessChanged() }
        libraryController = HomeLibraryController(pdfRepository, pref)
        libraryScanner = LibraryScanner.getInstance(applicationContext)
        scanSetupDialog = ScanSetupDialog(
            this,
            pref,
            onWholeDeviceChosen = {
                pref.setScanMode(ScanMode.WHOLE_DEVICE)
                libraryScanner.refresh(force = true)
                refresh()
            },
            onPickLocationsChosen = {
                scanLocationsPicker.launch(Intent(this, ScanLocationsActivity::class.java))
            },
        )

        recordOptionsDialog = RecordOptionsDialog(
            this,
            pdfRepository,
            coverCache,
            libraryScanner,
            historyPolicy,
            historyCleaner,
            lifecycleScope,
            onOpenIncognito = { item ->
                openInReader(item.uri, item.hash.takeUnless { item.isScanOnly }, incognito = true)
            },
            onChanged = ::refresh,
        )
        recentTab = RecentTabController(
            coverCache,
            lifecycleScope,
            this,
            libraryController,
        )
        libraryTab = LibraryTabController(
            this,
            pref,
            coverCache,
            lifecycleScope,
            this,
            selection = { selectionController.selectedHashes },
            libraryController = libraryController,
            libraryScanner = libraryScanner,
            hasFullAccess = { permissionManager.hasFullAccess() },
            onGrantAccessClicked = { permissionManager.requestFullAccess() },
            showScanSetup = ::shouldShowScanSetup,
            onScanSetupClicked = { scanSetupDialog.show() },
            onFilterChanged = ::refresh,
        )
        foldersTab = FoldersTabController(
            this,
            pref,
            coverCache,
            lifecycleScope,
            this,
            onGrantAccessClicked = { permissionManager.requestFullAccess() },
            hasFullAccess = { permissionManager.hasFullAccess() },
            showScanSetup = ::shouldShowScanSetup,
            onScanSetupClicked = { scanSetupDialog.show() },
            libraryController = libraryController,
            onNavigationChanged = ::updateFoldersBackState,
        )

        selectionController = HomeSelectionController(
            this,
            currentItems = { libraryTab.currentGridItems() },
            onSelectionChanged = { libraryTab.notifySelectionChanged() },
            onStatusBatch = ::statusBatch,
            onDeleteBatch = ::deleteBatch,
        )
        relocateController = RelocateController(
            this,
            pdfRepository,
            libraryScanner,
            lifecycleScope,
            onOpen = ::openInReader,
            onHealed = ::refresh,
        )

        setupPager()
        setupSearch()
        menuDialog = HomeMenuDialog(
            this,
            pref,
            currentTab = { currentTab() },
            onViewModeChanged = { libraryTab.applyViewMode() },
            onGridSizeChanged = { libraryTab.applyGridSize() },
            onSortChanged = ::refresh,
            onFolderModeChanged = { foldersTab.onModeChanged() },
            onShowStats = {
                showLibraryStatsDialog(this, allItems, libraryScanner.libraryEntries())
            },
            hasFullAccess = { permissionManager.hasFullAccess() },
            onScanLocations = { scanSetupDialog.show() },
        )
        onBackPressedDispatcher.addCallback(this, foldersBackCallback)
        binding.searchBar.inflateMenu(R.menu.home_search_bar)
        binding.searchBar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.homeMenuOption) {
                menuDialog.show()
                true
            } else {
                false
            }
        }
        binding.openPdfFab.setOnClickListener { pdfPicker.launch(arrayOf(PDF.FILE_TYPE)) }
        binding.openPdfFab.setOnLongClickListener {
            pickIncognito = true
            Toast.makeText(this, R.string.open_in_incognito_hint, Toast.LENGTH_SHORT).show()
            pdfPicker.launch(arrayOf(PDF.FILE_TYPE))
            true
        }

        lifecycleScope.launch {
            libraryScanner.index.collect { refresh() }
        }

        showWhatsNewOnFirstRun()
        handleRelocateIntent(intent)
    }

    private fun setupPager() {
        binding.homePager.adapter = HomeTabsAdapter { tab, recyclerView ->
            when (tab) {
                HomeTab.RECENT -> recentTab.attach(recyclerView)
                HomeTab.LIBRARY -> libraryTab.attach(recyclerView)
                HomeTab.FOLDERS -> foldersTab.attach(recyclerView)
            }
        }
        binding.homePager.offscreenPageLimit = HomeTab.entries.size - 1

        TabLayoutMediator(binding.homeTabs, binding.homePager) { tab, position ->
            tab.setText(
                when (HomeTab.entries[position]) {
                    HomeTab.RECENT -> R.string.home_tab_recent
                    HomeTab.LIBRARY -> R.string.home_tab_library
                    HomeTab.FOLDERS -> R.string.home_tab_folders
                }
            )
        }.attach()

        binding.homePager.setCurrentItem(pref.getHomeTab().ordinal, false)
        binding.homePager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pref.setHomeTab(HomeTab.entries[position])
                if (HomeTab.entries[position] != HomeTab.LIBRARY) {
                    selectionController.finish()
                }
                updateFoldersBackState()
            }
        })
    }

    private fun updateFoldersBackState() {
        foldersBackCallback.isEnabled =
            currentTab() == HomeTab.FOLDERS && foldersTab.canGoBack()
    }

    private fun currentTab(): HomeTab = HomeTab.entries[binding.homePager.currentItem]

    private fun showWhatsNewOnFirstRun() {
        if (pref.getShowFeaturesDialog()) {
            lifecycleScope.launch {
                delay(500)
                if (!isFinishing) {
                    startActivity(Intent(this@HomeActivity, WhatsNewActivity::class.java))
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

    private fun setupSearch() {
        binding.searchView.setupWithSearchBar(binding.searchBar)

        searchResultsAdapter = LibraryAdapter(coverCache, lifecycleScope, this)
        searchResultsAdapter.viewMode = HomeViewMode.LIST
        binding.searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.searchResultsRecyclerView.adapter = searchResultsAdapter

        binding.searchView.editText.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString().orEmpty()
            searchResultsAdapter.submitList(
                libraryController.searchAll(allItems, libraryScanner.libraryEntries(), query)
            )
        }
    }

    private fun shouldShowScanSetup(): Boolean {
        return permissionManager.hasFullAccess()
            && pref.getScanMode() == ScanMode.NOT_CONFIGURED
            && libraryScanner.index.value.loaded
    }

    private fun onStorageAccessChanged() {
        recentTab.onCoversChanged()
        libraryTab.onCoversChanged()
        foldersTab.onCoversChanged()
        searchResultsAdapter.notifyDataSetChanged()
        libraryScanner.refresh(force = true)
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            allItems = libraryController.loadLibrary()
            val scanIndex = libraryScanner.index.value
            recentTab.render(allItems)
            libraryTab.render(allItems)
            foldersTab.render(allItems, scanIndex.entries, scanIndex.scanning)
        }
    }

    private fun openInReader(uri: Uri, hash: String? = null, incognito: Boolean = false) {
        Intent(this, MainActivity::class.java).also { intent ->
            intent.data = uri
            intent.putExtra(EXTRA_FROM_HOME, true)
            hash?.let { intent.putExtra(EXTRA_RECORD_HASH, it) }
            if (incognito) {
                intent.putExtra(PDF.incognitoKey, true)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            startActivity(intent)
        }
    }

    override fun onItemClicked(item: HomeItem) {
        if (selectionController.active && currentTab() == HomeTab.LIBRARY) {
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
        if (binding.searchView.isShowing || currentTab() != HomeTab.LIBRARY) {
            return false
        }
        return selectionController.begin(item)
    }

    override fun onItemOptionsClicked(item: HomeItem) {
        recordOptionsDialog.show(item)
    }

    private fun statusBatch(items: List<HomeItem>) {
        val labels = ReadingStatus.entries
            .map { it.name.formatEnumToTitle() }
            .toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.home_set_status)
            .setItems(labels) { _, index ->
                lifecycleScope.launch {
                    val hashes = items.mapNotNull { recordOptionsDialog.ensureRecordHash(it) }
                    if (hashes.size < items.size && !historyPolicy.canRecord()) {
                        Toast.makeText(this@HomeActivity, R.string.history_action_blocked, Toast.LENGTH_SHORT).show()
                    }
                    pdfRepository.setReadingBatch(hashes, ReadingStatus.entries[index])
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
                    historyCleaner.deleteDocuments(items.map { it.hash })
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
        const val EXTRA_OPEN_ONLINE_DIALOG = "openOnlineDialog"
    }
}
