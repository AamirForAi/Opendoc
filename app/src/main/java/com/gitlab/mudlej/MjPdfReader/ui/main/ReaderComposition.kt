package com.gitlab.mudlej.MjPdfReader.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import com.github.barteksc.pdfviewer.PDFView.Configurator
import com.github.barteksc.pdfviewer.listener.OnTextSelectionChangeListener
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.scroll.ScrollHandle
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit
import com.gitlab.mudlej.MjPdfReader.data.signature.SignatureStore
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.enums.FileType
import com.gitlab.mudlej.MjPdfReader.manager.autoscroll.AutoScrollManager
import com.gitlab.mudlej.MjPdfReader.manager.autoscroll.AutoScrollManagerImpl
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManager
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManagerImpl
import com.gitlab.mudlej.MjPdfReader.manager.fullscreen.FullScreenOptionsManager
import com.gitlab.mudlej.MjPdfReader.manager.fullscreen.FullScreenOptionsManagerImpl
import com.gitlab.mudlej.MjPdfReader.repository.AppDatabase
import com.gitlab.mudlej.MjPdfReader.ui.about.AboutActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.showGoToPageDialog
import com.gitlab.mudlej.MjPdfReader.ui.showSearchDialog
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import com.gitlab.mudlej.MjPdfReader.util.AppSnackbar
import com.gitlab.mudlej.MjPdfReader.util.PersistedGrantKeeper
import com.gitlab.mudlej.MjPdfReader.util.navIntent
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReaderComposition(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val vm: ReaderViewModel,
    private val pref: Preferences,
) {

    private val ui: ReaderUi = activity
    private val doc = vm.doc
    private val scope = activity.lifecycleScope

    private val pdfPickerLauncher: ActivityResultLauncher<Array<String>> = activity.registerForActivityResult(OpenDocument()) { selectedDocumentUri ->
        if (selectedDocumentUri != null) {
            PersistedGrantKeeper.takeReadGrant(activity, selectedDocumentUri)
        }
        openSelectedDocument(selectedDocumentUri)
    }

    private val saveToDownloadPermissionLauncher: ActivityResultLauncher<String> = activity.registerForActivityResult(RequestPermission()) { granted ->
        onlinePdfController.saveDownloadedFileAfterPermissionRequest(granted)
    }

    val readFileErrorPermissionLauncher: ActivityResultLauncher<String> = activity.registerForActivityResult(RequestPermission()) { granted ->
        activity.restartAppIfGranted(granted)
    }

    private val settingsLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(StartActivityForResult()) {
        activity.displayFromUri(doc.uri)
    }

    private val updateAnnotationDestinationLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            annotationSaveController.handleDestinationResult(result.data)
        } else {
            annotationSaveController.clearPendingRequests()
        }
    }

    private val createAnnotationDestinationLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            annotationSaveController.handleDestinationResult(result.data)
        } else {
            annotationSaveController.clearPendingRequests()
        }
    }

    private val tableOfContentsLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(StartActivityForResult()) { result ->
        ui.hideProgress()
        readerNavigationController.handleTableOfContentsResult(result.resultCode, result.data)
    }

    private val userBookmarksLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(StartActivityForResult()) { result ->
        vm.bookmarksLoadedForHash = null
        activity.ensureUserBookmarksLoaded()
        readerNavigationController.handleUserBookmarksResult(result.resultCode, result.data)
    }

    private val navigationHistoryLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(StartActivityForResult()) { result ->
        ui.hideProgress()
        readerNavigationController.handleNavigationHistoryResult(result.resultCode, result.data)
    }

    private val linksLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(StartActivityForResult()) { result ->
        ui.hideProgress()
        readerNavigationController.handleLinksResult(result.resultCode, result.data)
    }

    private val searchLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(StartActivityForResult()) { result ->
        ui.hideProgress()
        readerNavigationController.handleSearchResult(result.resultCode, result.data)
    }

    private val textModeLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(StartActivityForResult()) { result ->
        ui.hideProgress()
        readerNavigationController.handleTextModeResult(result.resultCode, result.data)
    }

    val databaseManager: DatabaseManager = DatabaseManagerImpl(AppDatabase.getInstance(activity.applicationContext))
    val autoScrollSpeedStore = AutoScrollSpeedStore(doc, databaseManager, scope, backgroundSaveScope)
    val autoScrollManager: AutoScrollManager =
        AutoScrollManagerImpl(binding, vm, pref, autoScrollSpeedStore::onSpeedChanged)
    val fullScreenOptionsManager: FullScreenOptionsManager =
        FullScreenOptionsManagerImpl(binding, vm, pref.getHideDelay().toLong(), pref)
    val zoomSwipeLockController = ZoomSwipeLockController(binding, ::drawableOf)
    val brightnessController = BrightnessController(activity, binding, vm)
    val pdfThemeController = PdfThemeController(activity, binding, pref)
    val volumeKeyPager = VolumeKeyPager(binding, doc, pref)
    val mousePager = MousePager(binding, doc, pref)
    val printController = PrintController(activity, binding, doc, scope)
    val pageTextCopier = PageTextCopier(activity, binding, doc, scope)
    val screenshotController: ScreenshotController = ScreenshotController(
        activity,
        binding,
        doc,
        { fullScreenOptionsManager.showAllTemporarilyOrHide() },
        { uri -> activity.shareFile(uri, FileType.IMAGE) },
    )

    val annotationController: AnnotationController = AnnotationController(activity, binding, vm)
    val formFieldController = FormFieldController(activity, binding, ::onAnnotationEdit)
    val signatureController: SignatureController = SignatureController(
        activity,
        binding,
        vm,
        SignatureStore(activity),
        annotationController,
        ::onAnnotationEdit,
        ui::updateDirtyUi,
    )
    val inlineAnnotationActionController: InlineAnnotationActionController = InlineAnnotationActionController(
        activity,
        binding,
        { readerNavigationController.clearActiveSearchResultHighlight() },
        ::onAnnotationEdit,
        ui::updateDirtyUiPosition,
        { pref.getDetectExistingHighlights() },
        { pref.getHighlightColors() },
    ) { fullScreenOptionsManager.showAllTemporarilyOrHide() }
    val annotationSaveController: AnnotationSaveController = AnnotationSaveController(
        activity,
        binding,
        doc,
        annotationController,
        databaseManager,
        vm,
        scope,
        updateAnnotationDestinationLauncher,
        createAnnotationDestinationLauncher,
        { readerNavigationController.clearActiveSearchResultHighlight() },
        ui::updateDirtyUi,
        { signatureController.commitPendingSignature() },
    ) {
        ui.updateTitle()
    }
    val cropMarginsController: CropMarginsController = CropMarginsController(
        activity,
        binding,
        databaseManager,
        doc,
        scope,
        { vm.cropMarginsEnabled },
        ::setCropMarginsEnabled,
        vm::isCurrent,
        { configurator, pageNumber, cropMargins, viewState ->
            documentLoader.reloadWithCropMargins(configurator, pageNumber, cropMargins, viewState)
        },
    )
    val readingDirectionResolver = ReadingDirectionResolver(activity, doc, pref, databaseManager)
    val onlinePdfController: OnlinePdfController = OnlinePdfController(
        activity,
        binding,
        doc,
        scope,
        { saveToDownloadPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) },
        { bytes -> documentLoader.initPdfViewAndLoad(binding.pdfView.fromBytes(bytes)) },
        { uri -> ui.runAfterDirtyAnnotationPrompt { activity.displayFromUri(uri, savePassword = true) } },
    )

    val readerHistory: ReaderHistoryManager = ReaderHistoryManager({ binding.pdfView }, ::onHistoryChanged)
    val readerNavigationController: ReaderNavigationController = ReaderNavigationController(
        activity,
        binding,
        doc,
        readerHistory,
        activity::onPageDisplayed,
        ui::updateTitle,
        { intent -> tableOfContentsLauncher.launch(intent) },
        { intent -> userBookmarksLauncher.launch(intent) },
        { intent -> navigationHistoryLauncher.launch(intent) },
        { intent -> linksLauncher.launch(intent) },
        { intent -> searchLauncher.launch(intent) },
    )

    val actionResolver: ConfigurableActionResolver = ConfigurableActionResolver(
        doc::hasFile,
        pref::getHorizontalScroll,
        { vm.cropMarginsEnabled },
        { pdfThemeController.effectivePdfDarkTheme() },
        { pref.getPdfPagesTheme() == Preferences.themeSystem },
        { readerHistory.canGoBack() },
        { readerHistory.canGoForward() },
        { vm.bookmarkedPages.contains(doc.pageNumber) },
        createHandlers(),
    )
    val toolbarActionController = ToolbarActionController(
        actionResolver,
        pref::getPrimaryButtonAction,
        pref::getSecondaryButtonAction,
    )
    val fullScreenButtonController: FullScreenButtonController = FullScreenButtonController(
        activity,
        binding,
        pref,
        actionResolver,
        fullScreenOptionsManager,
        autoScrollManager,
    ) { brightnessController.hideControl() }
    val shortcutBarController: ShortcutBarController = ShortcutBarController(
        activity,
        binding,
        pref,
        actionResolver,
    ) { vm.isFullScreenToggled }
    val readerMenu: ReaderMenu = ReaderMenu(activity, actionResolver, doc::hasFile, ::toggleSecondBar)
    val fullscreenController: FullscreenController = FullscreenController(
        activity,
        binding,
        vm,
        pref,
        fullScreenOptionsManager,
        autoScrollManager,
        zoomSwipeLockController,
        brightnessController,
    ) { shortcutBarController.updateVisibility() }


    val tapDispatcher = TapDispatcher(listOf(
        { event -> inlineAnnotationActionController.handleImmediatePdfTap(event) },
        { event -> formFieldController.handlePdfTap(event) },
        { _ ->
            inlineAnnotationActionController.handleEmptyTap()
            true
        },
    ))
    val documentLoader: DocumentLoader = DocumentLoader(
        binding,
        vm,
        pref,
        databaseManager,
        readingDirectionResolver,
        scope,
        ui,
        activity::downloadOrShowDownloadedFile,
        ::decorateConfigurator,
    )
    val readingDirectionController: ReadingDirectionController = ReadingDirectionController(
        activity,
        doc,
        vm,
        pref,
        databaseManager,
        scope,
        readingDirectionResolver,
        documentLoader,
    )

    private var historyNavState = false to false

    init {
        inlineAnnotationActionController.configure { annotationSaveController.saveHighlights() }
        subscribeDocumentListeners()
    }

    private fun decorateConfigurator(configurator: Configurator): Configurator {
        return configurator
            .onDocumentInteraction { motionEvent -> autoScrollManager.handleUserInteraction(motionEvent) }
            .onTap { motionEvent -> tapDispatcher.dispatch(motionEvent) }
            .onTapUp { motionEvent -> inlineAnnotationActionController.handleImmediatePdfTap(motionEvent) }
            .linkHandler(readerNavigationController.createLinkHandler())
            .scrollHandle(createScrollHandle())
            .nightMode(pdfThemeController.effectivePdfDarkTheme())
            .onTextSelectionChange(object : OnTextSelectionChangeListener {
                override fun onTextSelectionChanged(viewBounds: RectF?, pageIndex: Int) {
                    inlineAnnotationActionController.showSelectionActions(viewBounds)
                }

                override fun onTextSelectionCleared() {
                    inlineAnnotationActionController.hideActions()
                }
            })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createScrollHandle(): ScrollHandle {
        val handle = DefaultScrollHandle(activity, false, pref.getShowScrollHandlePageCount())
        val fullScreenTouchListener = fullScreenOptionsManager.getOnTouchListener()
        handle.setOnTouchListener { view, motionEvent ->
            autoScrollManager.handleUserInteraction(motionEvent)
            fullScreenTouchListener.onTouch(view, motionEvent)
        }
        handle.setOnClickListener { goToPage() }
        return handle
    }

    private fun subscribeDocumentListeners() {
        documentLoader.subscribe(object : DocumentListener {
            override fun onDocumentReset() {
                autoScrollSpeedStore.flushPendingSave()
                pageTextCopier.resetForNewDocument()
            }
        })
        documentLoader.subscribe(object : DocumentListener {
            override fun onDocumentLoaded(event: DocumentLoadedEvent) {
                pdfThemeController.configureTheme()
            }
        })
        documentLoader.subscribe(object : DocumentListener {
            override fun onDocumentLoaded(event: DocumentLoadedEvent) {
                if (event.applyDocumentLoadDefaults) {
                    fullscreenController.checkAutoFullScreen()
                    activity.checkAlwaysHorizontal()
                    openTextModeByDefault()
                    configureButtonsLabels()
                }
                if (doc.uri != null) {
                    shortcutBarController.configure()
                }
                fullScreenButtonController.configure()
                fullscreenController.reapplyStateAfterLoad()
                autoScrollManager.setSpeed(doc.autoScrollSpeed ?: pref.getScrollSpeed())
                if (event.pageCount == 1) {
                    fullScreenOptionsManager.permanentlyHidePageHandle()
                }
            }
        })
        documentLoader.subscribe(object : DocumentListener {
            override fun onDocumentReset() {
                readerNavigationController.resetSearchResultState()
                readerNavigationController.resetTableOfContentsState()
                readerNavigationController.resetLinkJumpState()
                readerHistory.clear()
            }

            override fun onPageChanged(pageIndex: Int) {
                readerNavigationController.onPageChanged(pageIndex)
            }

            override fun onFileHashComputed() {
                readerNavigationController.onFileHashComputed()
            }
        })
        documentLoader.subscribe(object : DocumentListener {
            override fun onDocumentReset() {
                cropMarginsController.cancel()
            }

            override fun onDocumentLoaded(event: DocumentLoadedEvent) {
                cropMarginsController.startIfNeeded(
                    event.cachedCropMargins,
                    event.fileHash,
                    event.loadToken,
                    event.documentUri,
                    event.pageCount,
                )
            }

            override fun onRecordAvailable(fileHash: String) {
                scope.launch { cropMarginsController.onRecordAvailable(fileHash) }
            }
        })
        documentLoader.subscribe(object : DocumentListener {
            override fun onDocumentReset() {
                inlineAnnotationActionController.hideActions()
                signatureController.cancelPlacement()
            }

            override fun onDocumentLoaded(event: DocumentLoadedEvent) {
                activity.maybeRestoreAnnotations(event.documentUri, event.loadToken)
                signatureController.resumeRestoredPlacementIfNeeded()
            }
        })
        documentLoader.subscribe(object : DocumentListener {
            override fun onLoadFailed(reason: Throwable) {
                activity.handleFileOpeningError(reason)
            }
        })
    }

    fun wireViews() {
        binding.exitFullScreenButton.setOnClickListener { fullscreenController.exitFullscreen() }
        autoScrollManager.setup()
        brightnessController.attachSeekbarListener()
        binding.apply {
            rotateScreenButton.setOnClickListener { activity.rotateScreen() }
            brightnessButton.setOnClickListener { brightnessController.toggleControlVisibility() }
            screenshotButton.setOnClickListener { screenshotController.takeScreenshot() }
            toggleHorizontalSwipeButton.setOnClickListener { zoomSwipeLockController.toggleHorizontalSwipeLock() }
            toggleZoomLockButton.setOnClickListener { zoomSwipeLockController.toggleZoomLock() }
            toggleLabelButton.setOnClickListener { toggleLabels() }
            pickFileButton.setOnClickListener { pickFile() }
            discardAnnotationsFab.setOnClickListener { activity.confirmDiscardAnnotations() }
        }
        fullScreenButtonController.configure()
    }

    fun onResume() {
        fullScreenButtonController.configure()
        if (doc.hasFile()) {
            shortcutBarController.configure()
        } else {
            binding.secondBarScrollView.visibility = View.GONE
        }
        inlineAnnotationActionController.rebuildHighlightSwatches()
    }

    fun refreshActions() {
        ui.updateActionBar()
        fullScreenButtonController.configure()
        if (doc.hasFile()) {
            shortcutBarController.configure()
        } else {
            binding.secondBarScrollView.visibility = View.GONE
        }
    }

    fun setCropMarginsEnabled(enabled: Boolean) {
        vm.cropMarginsEnabled = enabled
        refreshActions()
    }

    fun pickFile() {
        ui.runAfterDirtyAnnotationPrompt { launchPdfPicker() }
    }

    fun openTextModeByDefault() {
        if (pref.getDefaultTextMode()) {
            navToTextMode()
        }
    }

    fun configureButtonsLabels() {
        if (pref.getHideButtonsLabels() == fullScreenOptionsManager.isLabelsVisible()) {
            fullScreenOptionsManager.toggleLabelVisibility(activity, ::drawableOf, activity::getString)
        }
    }

    private fun createHandlers(): ConfigurableActionResolver.Handlers {
        return ConfigurableActionResolver.Handlers(
            toggleFullscreen = { fullscreenController.toggleFullscreen() },
            exitFullscreen = { fullscreenController.exitFullscreen() },
            rotate = activity::rotateScreen,
            toggleHorizontalLock = { zoomSwipeLockController.toggleHorizontalSwipeLock() },
            readingDirection = ::showReadingDirectionDialog,
            toggleZoomLock = { zoomSwipeLockController.toggleZoomLock() },
            toggleCropMargins = activity::toggleCropMargins,
            screenshot = { screenshotController.takeScreenshot() },
            switchTheme = ::switchPdfTheme,
            navigateBack = { readerHistory.goBack() },
            navigateForward = { readerHistory.goForward() },
            showNavigationHistory = ::showNavigationHistory,
            reload = activity::reloadPdf,
            openLocal = ::pickFile,
            openOnline = { onlinePdfController.showOpenOnlinePdfDialog() },
            search = { showSearchDialog(activity, doc) { intent -> searchLauncher.launch(intent) } },
            goToPage = ::goToPage,
            extractText = { pageTextCopier.copyPageText() },
            textMode = ::navToTextMode,
            share = { activity.shareFile(doc.uri, FileType.PDF) },
            settings = { settingsLauncher.launch(Intent(activity, SettingsActivity::class.java)) },
            fileMetadata = activity::showFileMetadata,
            about = { activity.startActivity(navIntent(activity, AboutActivity::class.java)) },
            tableOfContents = { readerNavigationController.showTableOfContents() },
            toggleBookmark = activity::toggleCurrentPageBookmark,
            userBookmarks = ::showUserBookmarks,
            linksInFile = { readerNavigationController.showLinks() },
            print = ::printFile,
            addSignature = { signatureController.showSignatureDialog() },
        )
    }

    private fun openSelectedDocument(selectedDocumentUri: Uri?) {
        if (selectedDocumentUri == null) {
            return
        }
        if (doc.uri == null || selectedDocumentUri == doc.uri) {
            try {
                documentLoader.initPdf(selectedDocumentUri)
                activity.displayFromUri(doc.uri, true)
            } catch (e: Throwable) {
                Log.e(TAG, "openSelectedDocument: ", e)
                AppSnackbar.make(binding.root, R.string.file_opening_error, Snackbar.LENGTH_LONG).show()
            }
        } else {
            val intent = Intent(activity, activity.javaClass)
            intent.data = selectedDocumentUri
            activity.startActivity(intent)
        }
    }

    private fun launchPdfPicker() {
        try {
            pdfPickerLauncher.launch(arrayOf(PDF.FILE_TYPE))
        } catch (e: ActivityNotFoundException) {
            AppSnackbar.make(binding.root, R.string.toast_pick_file_error, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun onAnnotationEdit(edit: AnnotationEdit) {
        annotationController.recordEdit(edit)
        ui.updateDirtyUi()
    }

    private fun onHistoryChanged() {
        val navState = readerHistory.canGoBack() to readerHistory.canGoForward()
        if (navState != historyNavState) {
            historyNavState = navState
            refreshActions()
        }
    }

    private fun toggleSecondBar() {
        pref.setSecondBarEnabled(binding.secondBarScrollView.visibility != View.VISIBLE)
        shortcutBarController.updateVisibility()
    }

    private fun switchPdfTheme() {
        pdfThemeController.switchPdfTheme { ui.checkHasFile() }
        refreshActions()
    }

    private fun showReadingDirectionDialog() {
        if (!ui.checkHasFile()) {
            return
        }
        readingDirectionController.showDialog()
    }

    private fun showUserBookmarks() {
        if (!ui.checkHasFile()) {
            return
        }
        if (doc.fileHash == null) {
            AppSnackbar.make(binding.root, R.string.bookmark_hash_unavailable, Snackbar.LENGTH_SHORT).show()
            return
        }
        readerNavigationController.showUserBookmarks()
    }

    private fun showNavigationHistory() {
        if (!ui.checkHasFile()) {
            return
        }
        readerNavigationController.showNavigationHistory()
    }

    private fun printFile() {
        if (!ui.checkHasFile()) {
            return
        }
        printController.printFile()
    }

    private fun navToTextMode() {
        if (!ui.checkHasFile()) {
            return
        }
        val currentPageIndex = currentPdfViewPageIndex()
        Intent(activity, TextModeActivity::class.java).also {
            it.putExtra(PDF.filePathKey, doc.uri.toString())
            it.putExtra(PDF.passwordKey, doc.password)
            it.putExtra(PDF.pageNumberKey, currentPageIndex)
            doc.fileHash?.let { fileHash -> it.putExtra(PDF.fileHashKey, fileHash) }
            textModeLauncher.launch(it)
        }
    }

    private fun currentPdfViewPageIndex(): Int {
        val currentPage = binding.pdfView.currentPage.coerceAtLeast(0)
        val pageCount = binding.pdfView.pageCount
        return if (pageCount > 0) currentPage.coerceAtMost(pageCount - 1) else currentPage
    }

    private fun goToPage() {
        showGoToPageDialog(activity, binding.root, doc.pageNumber, doc.length) { pageIndex ->
            readerHistory.recordJump(ReaderHistoryManager.Origin.GO_TO, pageIndex)
            binding.pdfView.jumpTo(pageIndex)
        }
    }

    private fun toggleLabels() {
        fullScreenOptionsManager.toggleLabelVisibility(activity, ::drawableOf, activity::getString)
        pref.setHideButtonsLabels(!pref.getHideButtonsLabels())
    }

    private fun drawableOf(id: Int): Drawable? {
        return AppCompatResources.getDrawable(activity, id)
    }

    companion object {
        private const val TAG = "ReaderComposition"
        private val backgroundSaveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
