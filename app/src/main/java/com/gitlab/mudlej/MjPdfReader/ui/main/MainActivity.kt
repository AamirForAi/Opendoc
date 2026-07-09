/*
 *   MJ PDF
 *   Copyright (C) 2023 Mudlej
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *  --------------------------
 *  This code was previously licensed under
 *
 *  MIT License
 *
 *  Copyright (c) 2018 Gokul Swaminathan
 *  Copyright (c) 2023 Mudlej
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package com.gitlab.mudlej.MjPdfReader.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.github.barteksc.pdfviewer.model.CropMargins
import com.gitlab.mudlej.MjPdfReader.Launcher
import com.gitlab.mudlej.MjPdfReader.Launchers
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.*
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit
import com.gitlab.mudlej.MjPdfReader.data.signature.SignatureStore
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.databinding.PasswordDialogBinding
import com.gitlab.mudlej.MjPdfReader.enums.FileType
import com.gitlab.mudlej.MjPdfReader.enums.ReadingDirection
import com.gitlab.mudlej.MjPdfReader.manager.autoscroll.AutoScrollManager
import com.gitlab.mudlej.MjPdfReader.manager.autoscroll.AutoScrollManagerImpl
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManager
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManagerImpl
import com.gitlab.mudlej.MjPdfReader.manager.fullscreen.FullScreenOptionsManager
import com.gitlab.mudlej.MjPdfReader.manager.fullscreen.FullScreenOptionsManagerImpl
import com.gitlab.mudlej.MjPdfReader.manager.permission.PermissionManager
import com.gitlab.mudlej.MjPdfReader.repository.AppDatabase
import com.gitlab.mudlej.MjPdfReader.ui.*
import com.gitlab.mudlej.MjPdfReader.ui.about.AboutActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import com.gitlab.mudlej.MjPdfReader.util.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.shockwave.pdfium.PdfPasswordException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.util.*
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private companion object {
        val backgroundSaveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding

    private var doubleBackToExitPressedOnce = false
    private lateinit var autoScrollManager: AutoScrollManager
    private lateinit var fullScreenOptionsManager: FullScreenOptionsManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var databaseManager: DatabaseManager
    private lateinit var actionResolver: ConfigurableActionResolver
    private lateinit var toolbarActionController: ToolbarActionController
    private lateinit var fullScreenButtonController: FullScreenButtonController
    private lateinit var shortcutBarController: ShortcutBarController
    private lateinit var cropMarginsController: CropMarginsController
    private lateinit var annotationController: AnnotationController
    private lateinit var inlineAnnotationActionController: InlineAnnotationActionController
    private lateinit var formFieldController: FormFieldController
    private lateinit var signatureController: SignatureController
    private val printController by lazy { PrintController(this, binding, pdf, lifecycleScope) }
    private val volumeKeyPager by lazy { VolumeKeyPager(binding, pdf, pref) }
    private val zoomSwipeLockController by lazy { ZoomSwipeLockController(binding, ::drawableOf) }
    private val brightnessController by lazy { BrightnessController(this, binding, pdf) }
    private val pdfThemeController by lazy { PdfThemeController(this, binding, pref) }
    private val fullscreenController by lazy {
        FullscreenController(
            this,
            binding,
            pdf,
            pref,
            fullScreenOptionsManager,
            autoScrollManager,
            zoomSwipeLockController,
            brightnessController,
        ) { shortcutBarController.updateVisibility() }
    }
    private val autoScrollSpeedStore by lazy {
        AutoScrollSpeedStore(pdf, databaseManager, lifecycleScope, backgroundSaveScope)
    }
    private val onlinePdfController: OnlinePdfController by lazy {
        OnlinePdfController(
            this,
            binding,
            pdf,
            lifecycleScope,
            { launchers.saveToDownloadPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) },
            { bytes -> documentLoadController.initPdfViewAndLoad(binding.pdfView.fromBytes(bytes)) },
            { uri -> runAfterDirtyAnnotationPrompt { displayFromUri(uri, savePassword = true) } },
        )
    }
    private val screenshotController by lazy {
        ScreenshotController(
            this,
            binding,
            pdf,
            { fullScreenOptionsManager.showAllTemporarilyOrHide() },
            { uri -> shareFile(uri, FileType.IMAGE) },
        )
    }
    private lateinit var annotationSaveController: AnnotationSaveController
    private lateinit var pref: Preferences
    private val pdf = PDF()
    private val session = DocumentSession(pdf) { annotationController.acceptsDocumentUri(it) }
    private val readerNavigationController: ReaderNavigationController by lazy {
        ReaderNavigationController(
            this,
            binding,
            pdf,
            ::updateAppTitle,
            { intent -> bookmarksLauncher.launch(intent) },
            { intent -> linksLauncher.launch(intent) },
            { intent -> searchLauncher.launch(intent) },
        )
    }
    private val readerMenu by lazy {
        ReaderMenu(this, actionResolver, ::hasFile) { toggleSecondBar() }
    }
    private val pageTextCopier by lazy { PageTextCopier(this, binding, pdf, lifecycleScope) }
    private val readingDirectionResolver by lazy { ReadingDirectionResolver(this, pdf, pref, databaseManager) }
    private val readingDirectionController by lazy {
        ReadingDirectionController(
            this,
            pdf,
            session,
            pref,
            databaseManager,
            lifecycleScope,
            readingDirectionResolver,
            documentLoadController,
        )
    }
    private val documentLoadController by lazy {
        DocumentLoadController(
            this,
            binding,
            pdf,
            session,
            pref,
            databaseManager,
            readingDirectionResolver,
            lifecycleScope,
            annotationSaveController,
            cropMarginsController,
            autoScrollManager,
            pdfThemeController,
            readerNavigationController,
            fullScreenOptionsManager,
            inlineAnnotationActionController,
            ::prepareNewDocument,
            ::updateActionBarButtons,
            ::updateAppTitle,
            ::downloadOrShowDownloadedFile,
            ::handleReaderTap,
            ::goToPage,
            ::hideProgressBar,
            ::handleFileOpeningError,
            ::onDocumentLoaded,
        )
    }

    private lateinit var actionBarMenu: Menu
    private lateinit var appTitle: TextView
    private lateinit var appTitlePageNumber: TextView

    private val launchers = Launchers(
        Launcher(this, pdf).pdfPicker(),
        Launcher(this, pdf).saveToDownloadPermission { granted: Boolean ->
            onlinePdfController.saveDownloadedFileAfterPermissionRequest(granted)
        },
        Launcher(this, pdf).readFileErrorPermission(::restartAppIfGranted),
        Launcher(this, pdf).settings(::displayFromUri)
    )

    private val updateAnnotationDestinationLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            annotationSaveController.handleDestinationResult(result.data)
        } else {
            annotationSaveController.clearPendingRequests()
        }
    }

    private val createAnnotationDestinationLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            annotationSaveController.handleDestinationResult(result.data)
        } else {
            annotationSaveController.clearPendingRequests()
        }
    }

    private val bookmarksLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        hideProgressBar()
        readerNavigationController.handleBookmarksResult(result.resultCode, result.data)
    }

    private val linksLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        hideProgressBar()
        readerNavigationController.handleLinksResult(result.resultCode, result.data)
    }

    private val searchLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        hideProgressBar()
        readerNavigationController.handleSearchResult(result.resultCode, result.data)
    }

    private val textModeLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        hideProgressBar()
        readerNavigationController.handleTextModeResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pref = Preferences(PreferenceManager.getDefaultSharedPreferences(this))

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomActionBar()
        ColorUtil.colorize(this, window, supportActionBar)

        // To avoid FileUriExposedException, (https://stackoverflow.com/questions/38200282/)
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())

        buildControllers()
        documentLoadController.applyTileRenderingPreferences()

        openInitialDocument(savedInstanceState)
        setButtonsFunctionalities()
        overrideOnBackButtonPressed()
    }

    private fun buildControllers() {
        databaseManager = DatabaseManagerImpl(AppDatabase.getInstance(applicationContext))
        autoScrollManager = AutoScrollManagerImpl(binding, pdf, pref, autoScrollSpeedStore::onSpeedChanged)
        fullScreenOptionsManager = FullScreenOptionsManagerImpl(
            binding, pdf, pref.getHideDelay().toLong(), pref
        )
        actionResolver = ConfigurableActionResolver(
            ::hasFile,
            pref::getHorizontalScroll,
            ::isCropMarginsEnabled,
            createActionHandlers(),
        )
        toolbarActionController = ToolbarActionController(
            actionResolver,
            pref::getPrimaryButtonAction,
            pref::getSecondaryButtonAction,
        )
        fullScreenButtonController = FullScreenButtonController(
            this,
            binding,
            pref,
            actionResolver,
            fullScreenOptionsManager,
            autoScrollManager,
        ) { brightnessController.hideControl() }
        shortcutBarController = ShortcutBarController(
            this,
            binding,
            pref,
            actionResolver,
        ) { pdf.isFullScreenToggled }
        cropMarginsController = CropMarginsController(
            this,
            binding,
            databaseManager,
            pdf,
            lifecycleScope,
            ::isCropMarginsEnabled,
            ::setCropMarginsEnabled,
            session::isCurrent,
            { configurator, pageNumber, cropMargins, viewState ->
                documentLoadController.reloadWithCropMargins(configurator, pageNumber, cropMargins, viewState)
            },
        )
        annotationController = AnnotationController(this, binding, pdf)
        annotationSaveController = AnnotationSaveController(
            this,
            binding,
            pdf,
            annotationController,
            databaseManager,
            session,
            lifecycleScope,
            updateAnnotationDestinationLauncher,
            createAnnotationDestinationLauncher,
            ::clearActiveSearchResultHighlight,
            ::updateAnnotationDirtyUi,
            { signatureController.commitPendingSignature() },
        ) {
            updateAppTitle()
        }
        inlineAnnotationActionController = InlineAnnotationActionController(
            this,
            binding,
            ::clearActiveSearchResultHighlight,
            ::onAnnotationEdit,
            ::updateAnnotationSaveUiPosition,
            { pref.getDetectExistingHighlights() },
        ) { fullScreenOptionsManager.showAllTemporarilyOrHide() }
        formFieldController = FormFieldController(this, binding, ::onAnnotationEdit)
        signatureController = SignatureController(
            this,
            binding,
            SignatureStore(this),
            annotationController,
            ::onAnnotationEdit,
            ::updateAnnotationDirtyUi,
        )
        permissionManager = PermissionManager(this)
        inlineAnnotationActionController.configure { annotationSaveController.saveHighlights() }
    }

    private fun openInitialDocument(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState)
        }
        else {
            val intentUri = intent.data
            if (intentUri == null) {
                pickFile()
            } else {
                prepareNewDocument(intentUri)
            }
        }

        displayFromUri(pdf.uri, true)
    }

    fun initPdf(pdf: PDF, uri: Uri) {
        documentLoadController.initPdf(pdf, uri)
    }

    private fun prepareNewDocument(uri: Uri) {
        if (pdf.uri == uri) {
            return
        }
        autoScrollSpeedStore.flushPendingSave()
        cropMarginsController.cancel()
        session.beginNewDocument(uri, pref.getAlwaysHideMargins())
        pageTextCopier.resetForNewDocument()
        readerNavigationController.resetSearchResultState()
        readerNavigationController.resetBookmarkState()
        readerNavigationController.resetLinkJumpState()
        inlineAnnotationActionController.hideActions()
        signatureController.cancelPlacement()
        PdfBytesHolder.clear()
        annotationController.resetForDocument(uri)
        updateAnnotationDirtyUi()
    }

    private fun isCropMarginsEnabled() = session.cropMarginsEnabled

    private fun setCropMarginsEnabled(enabled: Boolean) {
        session.cropMarginsEnabled = enabled
        refreshConfiguredActions()
    }

    fun isDisplayingUri(uri: String): Boolean {
        return pdf.uri?.toString() == uri
    }

    private fun setCustomActionBar() {
        val actionBar = supportActionBar
        // Disable the default and enable the custom
        actionBar?.setDisplayShowTitleEnabled(false)
        actionBar?.setDisplayShowCustomEnabled(true)
        actionBar?.elevation = 0F

        val customView: View = layoutInflater.inflate(R.layout.actionbar_title, null)
        appTitlePageNumber = customView.findViewById(R.id.actionbarPageNumber)
        appTitle = customView.findViewById(R.id.actionbarTitle)

        fun titleClickListener() {
            val title = pdf.getTitle()
            if (title.isNotBlank()) {
                Snackbar.make(binding.root, title, Snackbar.LENGTH_LONG).show()
            }
        }
        appTitle.setOnClickListener { titleClickListener() }
        appTitlePageNumber.setOnClickListener { titleClickListener() }

        // Apply the custom view
        actionBar?.customView = customView
    }

    private fun pickFile() {
        runAfterDirtyAnnotationPrompt { launchPdfPicker() }
    }

    private fun launchPdfPicker() {
        try {
            launchers.pdfPicker.launch(arrayOf(PDF.FILE_TYPE))
        }
        catch (e: ActivityNotFoundException) {
            // alert user that file manager not working
            Snackbar.make(binding.root, R.string.toast_pick_file_error, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun runAfterDirtyAnnotationPrompt(discardAction: () -> Unit) {
        if (!annotationController.hasUnsavedAnnotations) {
            discardAction()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.unsaved_highlights)
            .setMessage(R.string.unsaved_highlights_prompt)
            .setPositiveButton(R.string.save_highlights) { _, _ ->
                annotationSaveController.saveHighlights(postSaveAction = discardAction)
            }
            .setNegativeButton(R.string.discard) { _, _ ->
                signatureController.cancelPlacement()
                annotationSaveController.clearPendingRequests()
                annotationController.clearJournal()
                updateAnnotationDirtyUi()
                discardAction()
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    fun displayFromUri(uri: Uri?, savePassword: Boolean = false) {
        documentLoadController.displayFromUri(uri, savePassword)
    }

    private fun updateAppTitle() {
        appTitle.text = pdf.getTitle()
        appTitlePageNumber.text = pdf.getPageCounterText()
        appTitlePageNumber.visibility = if (pref.getShowAppBarPageCount() && pdf.hasFile() && pdf.length > 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
        fullScreenOptionsManager.refreshInfo()
    }

    private fun onDocumentLoaded(
        pageCount: Int,
        cachedCropMargins: CropMargins?,
        fileHash: String?,
        loadToken: Long,
        documentUri: Uri?,
        applyDocumentLoadDefaults: Boolean,
    ) {
        if (applyDocumentLoadDefaults) {
            checkAutoFullScreen()
            checkAlwaysHorizontal()
            openTextModeByDefault()
            configureButtonsLabels()
        }
        if (pdf.uri != null) {
            setUpSecondBar()
        }
        fullScreenButtonController.configure()
        fullscreenController.reapplyStateAfterLoad()
        cropMarginsController.startIfNeeded(cachedCropMargins, fileHash, loadToken, documentUri, pageCount)
        maybeRestoreAnnotations(documentUri, loadToken)
        signatureController.resumeRestoredPlacementIfNeeded()
    }

    private fun openTextModeByDefault() {
        if (pref.getDefaultTextMode()) {
            navToTextMode()
        }
    }

    private fun updateActionBarButtons() {
        if (!::actionBarMenu.isInitialized) {
            return
        }

        toolbarActionController.update(actionBarMenu)
    }

    private fun refreshConfiguredActions() {
        updateActionBarButtons()
        if (::fullScreenButtonController.isInitialized) {
            fullScreenButtonController.configure()
        }
        if (::shortcutBarController.isInitialized) {
            if (hasFile()) {
                shortcutBarController.configure()
            } else {
                binding.secondBarScrollView.visibility = View.GONE
            }
        }
    }

    private fun hasFile() = pdf.hasFile()

    private fun createActionHandlers(): ConfigurableActionResolver.Handlers {
        return ConfigurableActionResolver.Handlers(
            toggleFullscreen = ::toggleFullscreen,
            exitFullscreen = ::exitFullscreen,
            rotate = ::rotateScreen,
            toggleHorizontalLock = { zoomSwipeLockController.toggleHorizontalSwipeLock() },
            readingDirection = ::showReadingDirectionDialog,
            toggleZoomLock = { zoomSwipeLockController.toggleZoomLock() },
            toggleCropMargins = ::toggleCropMargins,
            screenshot = ::takeScreenshot,
            switchTheme = ::switchPdfTheme,
            reload = ::reloadPdf,
            openLocal = ::pickFile,
            openOnline = ::showOpenOnlinePdfDialog,
            search = { showSearchDialog(this, pdf) { intent -> searchLauncher.launch(intent) } },
            goToPage = ::goToPage,
            extractText = ::copyPageText,
            textMode = ::navToTextMode,
            share = { shareFile(pdf.uri, FileType.PDF) },
            settings = ::navToAppSettings,
            fileMetadata = ::showFileMetadata,
            about = { startActivity(navIntent(this, AboutActivity::class.java)) },
            tableOfContents = ::showBookmarks,
            linksInFile = ::showLinks,
            print = ::printFile,
            addSignature = { signatureController.showSignatureDialog() },
        )
    }

    private fun checkAutoFullScreen() {
        fullscreenController.checkAutoFullScreen()
    }

    private fun checkAlwaysHorizontal() {
        if (pref.getAlwaysHorizontal() && pdf.isPortrait) {
            rotateScreen()
        }
        if (!pref.getAlwaysHorizontal() && !pdf.isPortrait) {
            rotateScreen()
        }
    }

    private fun copyPageText() {
        pageTextCopier.copyPageText()
    }

    private fun onAnnotationEdit(edit: AnnotationEdit) {
        annotationController.recordEdit(edit)
        updateAnnotationDirtyUi()
    }

    private fun handleReaderTap(event: MotionEvent): Boolean {
        if (inlineAnnotationActionController.handleImmediatePdfTap(event)) {
            return true
        }
        if (formFieldController.handlePdfTap(event)) {
            return true
        }
        inlineAnnotationActionController.handleEmptyTap()
        return true
    }

    private fun maybeRestoreAnnotations(documentUri: Uri?, loadToken: Long) {
        val uri = documentUri ?: return
        lifecycleScope.launch {
            val hasJournal = withContext(Dispatchers.IO) { annotationController.hasJournal(uri) }
            if (!hasJournal || !session.isCurrent(loadToken, uri)) {
                return@launch
            }
            if (annotationController.isSessionOwned(uri)) {
                replayAnnotations(uri, loadToken)
            } else {
                promptRestoreAnnotations(uri, loadToken)
            }
        }
    }

    private fun promptRestoreAnnotations(documentUri: Uri, loadToken: Long) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restore_unsaved_highlights_title)
            .setMessage(R.string.restore_unsaved_highlights_message)
            .setCancelable(false)
            .setPositiveButton(R.string.restore) { _, _ ->
                lifecycleScope.launch { replayAnnotations(documentUri, loadToken) }
            }
            .setNegativeButton(R.string.discard) { _, _ ->
                annotationController.clearJournal(documentUri)
                updateAnnotationDirtyUi()
            }
            .show()
    }

    private suspend fun replayAnnotations(documentUri: Uri, loadToken: Long) {
        if (!session.isCurrent(loadToken, documentUri)) {
            return
        }
        annotationController.replayJournal()
        updateAnnotationDirtyUi()
    }

    private fun updateAnnotationDirtyUi() {
        val visible = annotationController.hasUnsavedAnnotations
        binding.saveAnnotationsFab.visibility = if (visible) View.VISIBLE else View.GONE
        binding.unsavedHighlightsChip.visibility = if (visible) View.VISIBLE else View.GONE
        binding.saveAnnotationsFab.isEnabled = visible && !annotationController.isSaving
        updateAnnotationSaveUiPosition()
    }

    private fun updateAnnotationSaveUiPosition() {
        val params = binding.saveAnnotationsFab.layoutParams as ConstraintLayout.LayoutParams
        val defaultBottomMargin = (24 * resources.displayMetrics.density).toInt()
        val cardAtBottom = inlineAnnotationActionController.isCardAtBottom()
        val bottomMargin = if (cardAtBottom && binding.textSelectionActionCard.height > 0) {
            binding.textSelectionActionCard.height + (32 * resources.displayMetrics.density).toInt()
        } else {
            defaultBottomMargin
        }
        if (params.bottomMargin != bottomMargin) {
            params.bottomMargin = bottomMargin
            binding.saveAnnotationsFab.layoutParams = params
        }
    }

    private fun setUpSecondBar() {
        shortcutBarController.configure()
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setButtonsFunctionalities() {
        exitFullScreenListener(binding)
        autoScrollManager.setup()
        brightnessController.attachSeekbarListener()
        binding.apply {
            rotateScreenButton.setOnClickListener { rotateScreen() }
            brightnessButton.setOnClickListener { brightnessController.toggleControlVisibility() }
            screenshotButton.setOnClickListener { takeScreenshot() }
            toggleHorizontalSwipeButton.setOnClickListener { zoomSwipeLockController.toggleHorizontalSwipeLock() }
            toggleZoomLockButton.setOnClickListener { zoomSwipeLockController.toggleZoomLock() }
            toggleLabelButton.setOnClickListener { toggleLabelButtonListener() }
            pickFileButton.setOnClickListener { pickFile() }
        }
        fullScreenButtonController.configure()
    }

    private fun configureButtonsLabels() {
        if (pref.getHideButtonsLabels() == fullScreenOptionsManager.isLabelsVisible()) {
            fullScreenOptionsManager.toggleLabelVisibility(this@MainActivity, ::drawableOf, ::getString)
        }
    }

    private fun toggleLabelButtonListener() {
        fullScreenOptionsManager.toggleLabelVisibility(this@MainActivity, ::drawableOf, ::getString)
        pref.setHideButtonsLabels(!pref.getHideButtonsLabels())
    }

    private fun rotateScreen() {
        requestedOrientation = if (pdf.isPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        pdf.togglePortrait()
    }

    private fun exitFullScreenListener(binding: ActivityMainBinding) {
        binding.exitFullScreenButton.setOnClickListener { exitFullscreen() }
    }

    private fun exitFullscreen() {
        fullscreenController.exitFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        fullscreenController.refreshOnWindowFocus(hasFocus)
    }

    public override fun onResume() {
        super.onResume()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (pref.getScreenOn()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (::actionBarMenu.isInitialized) {
            updateActionBarButtons()
        }
        if (::pref.isInitialized) {
            fullScreenButtonController.configure()
            if (hasFile()) {
                shortcutBarController.configure()
            } else {
                binding.secondBarScrollView.visibility = View.GONE
            }
        }

        // check if there is a pdf at first

        if (pdf.uri != null) {
            binding.pickFileButton.visibility = View.GONE
        }
        else {
            binding.pickFileButton.visibility = View.VISIBLE
        }

        // restore the full screen mode if was toggled On
        restoreFullScreenIfNeeded()
    }

    private fun restoreFullScreenIfNeeded() {
        fullscreenController.restoreFullScreenIfNeeded()
    }

    private fun shareFile(uri: Uri?, type: FileType) {
        if (uri == null) {
            checkHasFile()  // only to show the message
            return
        }
        val sharingIntent: Intent =
            if (uri.scheme != null && uri.scheme!!.startsWith("http")) {
                plainTextShareIntent(getString(R.string.share_file), pdf.uri.toString())
            }
            else if (type == FileType.PDF) {
                fileShareIntent(getString(R.string.share_file), pdf.name, uri)
            }
            else if (type == FileType.IMAGE) {
                imageShareIntent(getString(R.string.share_file), pdf.name, uri)
            }
            else {
                return
            }

        try {
            startActivity(sharingIntent)
        }
        catch (e: Throwable) {
            Snackbar.make(binding.root, "Error sharing the file. (${e.message})", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun handleFileOpeningError(exception: Throwable) {
        val fileHash = pdf.fileHash
        if (exception is PdfPasswordException && fileHash != null) {
            if (pdf.password != null) {
                Snackbar.make(binding.root, R.string.wrong_password, Snackbar.LENGTH_SHORT).show()
                pdf.password = null         // prevent the toast if the user rotates the screen
            }

            lifecycleScope.launch {
                pdf.password = databaseManager.findPdfPassword(fileHash)
                withContext(Dispatchers.Main) {
                    if (pdf.password != null) {
                        displayFromUri(pdf.uri)
                    }
                    else {
                        askForPdfPassword()
                    }
                }
            }
        }
        else if (couldNotOpenFileDueToMissingPermission(exception)) {
            launchers.readFileErrorPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        else if (shouldReturnToHomeForRelocate(exception)) {
            returnToHomeForRelocate()
        }
        else {
            Snackbar.make(binding.root, R.string.file_opening_error, Snackbar.LENGTH_LONG).show()
            Log.e(TAG, getString(R.string.file_opening_error), exception)
        }
    }

    private fun shouldReturnToHomeForRelocate(exception: Throwable): Boolean {
        if (!intent.getBooleanExtra(HomeActivity.EXTRA_FROM_HOME, false)) {
            return false
        }
        if (intent.getStringExtra(HomeActivity.EXTRA_RECORD_HASH) == null) {
            return false
        }
        var cause: Throwable? = exception
        while (cause != null) {
            if (cause is SecurityException || cause is FileNotFoundException) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun returnToHomeForRelocate() {
        Intent(this, HomeActivity::class.java).also { homeIntent ->
            homeIntent.putExtra(
                HomeActivity.EXTRA_RELOCATE_HASH,
                intent.getStringExtra(HomeActivity.EXTRA_RECORD_HASH)
            )
            startActivity(homeIntent)
        }
        finish()
    }

    private fun couldNotOpenFileDueToMissingPermission(e: Throwable): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) return false
        val exceptionMessage = e.message
        return e is FileNotFoundException && exceptionMessage != null
            && exceptionMessage.contains(getString(R.string.permission_denied))
    }

    private fun restartAppIfGranted(isPermissionGranted: Boolean) {
        if (isPermissionGranted) {
            // This is a quick and dirty way to make the system restart the current activity *and the current app process*.
            // This is needed because on Android 6 storage permission grants do not take effect until
            // the app process is restarted.
            exitProcess(0)
        }
        else {
            Snackbar.make(binding.root, R.string.file_opening_error, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun toggleFullscreen() {
        fullscreenController.toggleFullscreen()
    }

    private fun reloadPdf() {
        if (checkHasFile()) {
            recreate()
        }
    }

    private fun toggleCropMargins() {
        if (!checkHasFile()) {
            return
        }
        val enableCropMargins = !isCropMarginsEnabled()
        setCropMarginsEnabled(enableCropMargins)
        if (enableCropMargins) {
            cropMarginsController.startOrApply(
                pdf.fileHash,
                session.currentLoadToken,
                pdf.uri,
                binding.pdfView.pageCount,
            )
        } else {
            cropMarginsController.cancel()
            recreate()
        }
    }

    private fun downloadOrShowDownloadedFile(uri: Uri) {
        onlinePdfController.downloadOrShowDownloadedFile(uri, lastCustomNonConfigurationInstance)
    }

    override fun onRetainCustomNonConfigurationInstance(): Any? {
        return onlinePdfController.retainSnapshot()
    }

    override fun onStop() {
        if (::databaseManager.isInitialized) {
            autoScrollSpeedStore.flushPendingSave()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (::databaseManager.isInitialized) {
            autoScrollSpeedStore.flushPendingSave()
        }
        if (::cropMarginsController.isInitialized) {
            cropMarginsController.cancel()
        }
        if (::inlineAnnotationActionController.isInitialized) {
            inlineAnnotationActionController.hideActions()
        }
        super.onDestroy()
    }

    fun hideProgressBar() {
        binding.progressBar.visibility = View.GONE
        binding.progressBar.isIndeterminate = true
        binding.progressBar.progress = 0
    }

    private fun navToAppSettings() {
        launchers.settings.launch(Intent(this, SettingsActivity::class.java))
    }

    private fun printFile() {
        if (!checkHasFile()) {
            return
        }
        printController.printFile()
    }

    private fun askForPdfPassword() {
        val dialogBinding = PasswordDialogBinding.inflate(layoutInflater)
        showAskForPasswordDialog(this, pdf, dialogBinding, ::displayFromUri)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        this.actionBarMenu = menu
        menu.showOptionalIcons(this)
        updateActionBarButtons()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.toolbarPrimaryActionOption,
            R.id.toolbarSecondaryActionOption -> if (!toolbarActionController.handle(item)) return super.onOptionsItemSelected(item)
            R.id.readerActionsOption -> showReaderActions()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun toggleSecondBar() {
        binding.apply {
            if (secondBarScrollView.visibility == View.VISIBLE) {
                pref.setSecondBarEnabled(false)
                shortcutBarController.updateVisibility()
            }
            else {
                pref.setSecondBarEnabled(true)
                shortcutBarController.updateVisibility()
            }
        }
    }

    private fun showLinks() {
        readerNavigationController.showLinks()
    }

    private fun showBookmarks() {
        readerNavigationController.showBookmarks()
    }

    private fun clearActiveSearchResultHighlight() {
        readerNavigationController.clearActiveSearchResultHighlight()
    }

    private fun goToPage() {
        fun goToPage(pageIndex: Int) {
            binding.pdfView.jumpTo(pageIndex)
        }
        showGoToPageDialog(this, binding.root, pdf.pageNumber, pdf.length, ::goToPage)
    }

    private fun showReadingDirectionDialog() {
        if (!checkHasFile()) {
            return
        }
        readingDirectionController.showDialog()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (volumeKeyPager.handleKeyDown(keyCode)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun navToTextMode() {
        if (!checkHasFile()) {
            return
        }

        val currentPageIndex = currentPdfViewPageIndex()
        pdf.pageNumber = currentPageIndex
        Intent(this, TextModeActivity::class.java).also {
            it.putExtra(PDF.filePathKey, pdf.uri.toString())
            it.putExtra(PDF.passwordKey, pdf.password)
            it.putExtra(PDF.pageNumberKey, currentPageIndex)
            pdf.fileHash?.let { fileHash -> it.putExtra(PDF.fileHashKey, fileHash) }
            textModeLauncher.launch(it)
        }
    }

    private fun currentPdfViewPageIndex(): Int {
        val currentPage = binding.pdfView.currentPage.coerceAtLeast(0)
        val pageCount = binding.pdfView.pageCount
        return if (pageCount > 0) currentPage.coerceAtMost(pageCount - 1) else currentPage
    }

    private fun showReaderActions() {
        readerMenu.show()
    }

    private fun showFileMetadata() {
        if (!checkHasFile()) {
            return
        }

        val uri = pdf.uri
        lifecycleScope.launch {
            val fileSizeBytes = withContext(Dispatchers.IO) { queryFileSizeBytes(uri) }
            val pageSize = withContext(Dispatchers.Default) {
                PdfPropertiesSummary.formatPageSizes(binding.pdfView, getString(R.string.pdf_page_size_mixed))
            }
            val fonts = withContext(Dispatchers.Default) {
                PdfPropertiesSummary.formatFonts(
                    binding.pdfView,
                    getString(R.string.font_embedded),
                    getString(R.string.font_not_embedded),
                )
            }
            showMetaDialog(this@MainActivity, binding.pdfView.documentMeta, pdf.name, fileSizeBytes, pageSize, fonts)
        }
    }

    private fun queryFileSizeBytes(uri: Uri?): Long? {
        if (uri == null) {
            return null
        }
        val heldBytes = if (PdfBytesHolder.uri == uri.toString()) PdfBytesHolder.pdfByte else null
        if (heldBytes != null) {
            return heldBytes.size.toLong()
        }
        return runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                fd.statSize.takeIf { it >= 0 }
            }
        }.getOrNull()
    }

    private fun showOpenOnlinePdfDialog() {
        onlinePdfController.showOpenOnlinePdfDialog()
    }

    private fun checkHasFile(): Boolean {
        if (!pdf.hasFile()) {
            Snackbar.make(
                binding.root, getString(R.string.no_pdf_in_app),
                Snackbar.LENGTH_LONG
            ).show()
            return false
        }
        return true
    }

    private fun switchPdfTheme() {
        pdfThemeController.switchPdfTheme(::checkHasFile)
    }

    private fun takeScreenshot() {
        screenshotController.takeScreenshot()
    }

    private fun drawableOf(id: Int): Drawable? {
        return AppCompatResources.getDrawable(this, id)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(PDF.uriKey, pdf.uri)
        outState.putString(PDF.fileHashKey, pdf.fileHash)
        outState.putInt(PDF.pageNumberKey, pdf.pageNumber)
        outState.putString(PDF.passwordKey, pdf.password)
        outState.putString(PDF.readingDirectionOverrideKey, pdf.readingDirectionOverride?.id)
        outState.putString(PDF.detectedReadingDirectionKey, pdf.detectedReadingDirection?.id)
        outState.putString(PDF.effectiveReadingDirectionKey, pdf.effectiveReadingDirection.id)
        outState.putBoolean(PDF.hasUnsavedAnnotationsKey, annotationController.hasUnsavedAnnotations)
        outState.putStringArrayList(PDF.sessionOwnedAnnotationKeysKey, annotationController.sessionOwnedKeysForState())
        outState.putBoolean(PDF.isPortraitKey, pdf.isPortrait)
        outState.putBoolean(PDF.isFullScreenToggledKey, pdf.isFullScreenToggled)
        pdf.autoScrollSpeed?.let { outState.putInt(PDF.autoScrollSpeedKey, it) }
        outState.putBoolean(PDF.cropMarginsEnabledKey, isCropMarginsEnabled())
        val viewState = session.saveViewState(outState, binding.pdfView.captureViewState())
        outState.putFloat(PDF.zoomKey, viewState?.zoom ?: pdf.zoom)
        outState.putBoolean(PDF.isExtractingTextFinishedKey, pdf.isExtractingTextFinished)
        readerNavigationController.saveState(outState)
        signatureController.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun restoreInstanceState(savedState: Bundle) {
        pdf.uri = savedState.getParcelable(PDF.uriKey)
        pdf.fileHash = savedState.getString(PDF.fileHashKey)
        pdf.pageNumber = savedState.getInt(PDF.pageNumberKey)
        pdf.password = savedState.getString(PDF.passwordKey)
        pdf.readingDirectionOverride = ReadingDirection.fromOverrideId(
            savedState.getString(PDF.readingDirectionOverrideKey),
        )
        pdf.detectedReadingDirection = ReadingDirection.fromId(savedState.getString(PDF.detectedReadingDirectionKey))
        pdf.effectiveReadingDirection = ReadingDirection.fromId(
            savedState.getString(PDF.effectiveReadingDirectionKey),
        ) ?: ReadingDirection.LEFT_TO_RIGHT
        pdf.isPortrait = savedState.getBoolean(PDF.isPortraitKey, true)
        pdf.isFullScreenToggled = savedState.getBoolean(PDF.isFullScreenToggledKey)
        pdf.autoScrollSpeed = savedState.takeIf { it.containsKey(PDF.autoScrollSpeedKey) }
            ?.getInt(PDF.autoScrollSpeedKey)
        session.cropMarginsEnabled = savedState.getBoolean(PDF.cropMarginsEnabledKey, pref.getAlwaysHideMargins())
        session.pendingViewState = session.restoreViewState(savedState)
        pdf.zoom = session.pendingViewState?.zoom ?: savedState.getFloat(PDF.zoomKey, 1f)
        pdf.isExtractingTextFinished = savedState.getBoolean(PDF.isExtractingTextFinishedKey)
        readerNavigationController.restoreState(savedState)
        annotationController.resetForDocument(pdf.uri)
        annotationController.restoreSessionOwnedKeys(
            savedState.getStringArrayList(PDF.sessionOwnedAnnotationKeysKey),
        )
        if (savedState.getBoolean(PDF.hasUnsavedAnnotationsKey, false)) {
            annotationController.markDirty()
            annotationController.markSessionOwned(pdf.uri)
        }
        signatureController.restoreState(savedState)
        updateAnnotationDirtyUi()
    }


    private fun overrideOnBackButtonPressed() {
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (annotationController.hasUnsavedAnnotations) {
                    runAfterDirtyAnnotationPrompt {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                    return
                }
                if (!pref.getDoubleTapToExitEnabled()
                    || intent.getBooleanExtra(HomeActivity.EXTRA_FROM_HOME, false)
                    || doubleBackToExitPressedOnce
                ) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    Snackbar.make(binding.root, getString(R.string.press_back_again), Snackbar.LENGTH_LONG).show()
                    doubleBackToExitPressedOnce = true

                    lifecycleScope.launch {
                        delay(2500)
                        doubleBackToExitPressedOnce = false
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

}
