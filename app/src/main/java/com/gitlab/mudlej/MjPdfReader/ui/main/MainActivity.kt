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
import android.app.ActivityManager
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.*
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.*
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.PDFView.Configurator
import com.github.barteksc.pdfviewer.listener.OnTextSelectionChangeListener
import com.github.barteksc.pdfviewer.model.CropMargins
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.scroll.ScrollHandle
import com.github.barteksc.pdfviewer.util.Constants
import com.github.barteksc.pdfviewer.util.FitPolicy
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
import com.gitlab.mudlej.MjPdfReader.repository.PdfRecord
import com.gitlab.mudlej.MjPdfReader.ui.*
import com.gitlab.mudlej.MjPdfReader.ui.about.AboutActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import com.gitlab.mudlej.MjPdfReader.util.*
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.shockwave.pdfium.PdfPasswordException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.time.LocalDateTime
import java.util.*
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private data class ReadingDirectionLoadState(
        val overrideDirection: ReadingDirection?,
        val detectedDirection: ReadingDirection?,
        val effectiveDirection: ReadingDirection,
    )

    private companion object {
        const val TILE_CACHE_PIXEL_BUDGET = 2 * 120 * 256 * 256
        const val MIN_TILE_CACHE_SIZE = 24
        const val MAX_TILE_CACHE_SIZE = 480
        val backgroundSaveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private val shouldStopExtracting: MutableMap<Int, Boolean> = mutableMapOf()
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
            { launchers.saveToDownloadPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) },
            { bytes -> initPdfViewAndLoad(binding.pdfView.fromBytes(bytes)) },
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
    private val readerNavigationController by lazy {
        ReaderNavigationController(this, binding, pdf, ::updateAppTitle)
    }
    private val readerMenu by lazy {
        ReaderMenu(this, actionResolver, ::hasFile) { toggleSecondBar() }
    }

    private lateinit var actionBarMenu: Menu

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

    private lateinit var appTitle: TextView
    private lateinit var appTitlePageNumber: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomActionBar()
        ColorUtil.colorize(this, window, supportActionBar)

        // To avoid FileUriExposedException, (https://stackoverflow.com/questions/38200282/)
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())

        // init
        pref = Preferences(PreferenceManager.getDefaultSharedPreferences(this))
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
            ::reloadWithCropMargins,
        )
        annotationController = AnnotationController(this, binding, pdf)
        annotationSaveController = AnnotationSaveController(
            this,
            binding,
            pdf,
            annotationController,
            databaseManager,
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

        applyTileRenderingPreferences()

        // Show Intro Activity and Features Dialog on the first install
        if (pref.getFirstInstall()) {
            onFirstInstall()
            finish()
            return
        }

        // navigate to settings to get permission to manage storage

        // Create PDF by restoring it in case of an activity restart OR ...
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
        setButtonsFunctionalities()
        showAppFeaturesDialogOnFirstRun()
        overrideOnBackButtonPressed()
    }

    fun initPdf(pdf: PDF, uri: Uri) {
        prepareNewDocument(uri)
        val loadToken = session.currentLoadToken
        lifecycleScope.launch {
            val hash = computeHash(this@MainActivity, pdf)
            if (session.isCurrent(loadToken, uri)) {
                pdf.fileHash = hash
            }
        }
    }

    private fun prepareNewDocument(uri: Uri) {
        if (pdf.uri == uri) {
            return
        }
        autoScrollSpeedStore.flushPendingSave()
        cropMarginsController.cancel()
        session.beginNewDocument(uri, pref.getAlwaysHideMargins())
        shouldStopExtracting.clear()
        showNoTextInPage = true
        resetSearchResultState()
        resetBookmarkState()
        resetLinkJumpState()
        inlineAnnotationActionController.hideActions()
        signatureController.cancelPlacement()
        PdfBytesHolder.clear()
        annotationController.resetForDocument(uri)
        updateAnnotationDirtyUi()
    }

    private fun resetSearchResultState() {
        readerNavigationController.resetSearchResultState()
    }

    private fun resetBookmarkState() {
        readerNavigationController.resetBookmarkState()
    }

    private fun resetLinkJumpState() {
        readerNavigationController.resetLinkJumpState()
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

    private fun onFirstInstall() {
        // To avoid com.github.paolorotolo.appintro.AppIntroBaseFragment.onCreateView
        // android.content.res.Resources$NotFoundException
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            startActivity(Intent(this, MainIntroActivity::class.java))
        }
        pref.setFirstInstall(false)
        pref.setShowFeaturesDialog(true)
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
        if (uri == null) {
            updateActionBarButtons()
            return
        }

        prepareNewDocument(uri)

        pdf.name = getFileName(this, uri)
        updateActionBarButtons()
        updateAppTitle()
        pdf.resetLength()

        setTaskDescription(ActivityManager.TaskDescription(pdf.name))
        val scheme = uri.scheme
        if (scheme != null && scheme.contains("http")) {
            downloadOrShowDownloadedFile(uri)
        } // temporary solution for files opened via nextcloud
        else if (scheme != null && scheme.contains("org.nextcloud.documents")){
            downloadOrShowDownloadedFile(uri)
        }
        else {
            loadCurrentDocument(savePassword)
        }
    }

    private fun loadCurrentDocument(savePassword: Boolean = false) {
        val uri = pdf.uri ?: return
        val bytes = if (PdfBytesHolder.uri == uri.toString()) PdfBytesHolder.pdfByte else null
        val configurator = if (bytes != null) {
            binding.pdfView.fromBytes(bytes)
        } else {
            binding.pdfView.fromUri(uri)
        }
        initPdfViewAndLoad(configurator, savePassword = savePassword)
    }

    private fun updateAppTitle() {
        appTitle.text = pdf.getTitleWithPageNumber()
        fullScreenOptionsManager.refreshInfo()
    }

    private fun initPdfViewAndLoad(viewConfigurator: Configurator, savePassword: Boolean = false) {
        val loadToken = session.currentLoadToken
        val documentUri = pdf.uri
        val viewState = session.pendingViewState
        lifecycleScope.launch {
            val hash = pdf.fileHash ?: computeHash(this@MainActivity, pdf)
            if (!session.isCurrent(loadToken, documentUri)) {
                return@launch
            }
            if (hash == null && pdf.pageNumber == 0) {
                showFailedToComputeHashError()
                return@launch
            }

            if (hash != null) {
                pdf.fileHash = hash
            }

            annotationSaveController.resolveCurrentDestination(documentUri)
            if (!session.isCurrent(loadToken, documentUri)) {
                return@launch
            }

            val pageNumber = if (pdf.pageNumber == 0 && hash != null) {
                databaseManager.findPageNumber(hash)
            } else {
                pdf.pageNumber
            }
            if (!session.isCurrent(loadToken, documentUri)) {
                return@launch
            }

            val autoScrollSpeed = hash?.let { databaseManager.findAutoScrollSpeed(it) }
            if (!session.isCurrent(loadToken, documentUri)) {
                return@launch
            }

            val readingDirectionState = resolveReadingDirection(hash, documentUri)
            if (!session.isCurrent(loadToken, documentUri)) {
                return@launch
            }

            val cachedCropMargins = cropMarginsController.findCached(hash)
            if (!session.isCurrent(loadToken, documentUri)) {
                return@launch
            }
            pdf.pageNumber = pageNumber
            pdf.autoScrollSpeed = pdf.autoScrollSpeed ?: autoScrollSpeed
            pdf.readingDirectionOverride = readingDirectionState.overrideDirection
            pdf.detectedReadingDirection = readingDirectionState.detectedDirection
            pdf.effectiveReadingDirection = readingDirectionState.effectiveDirection
            withContext(Dispatchers.Main) {
                if (session.isCurrent(loadToken, documentUri)) {
                    autoScrollManager.setSpeed(pdf.autoScrollSpeed ?: pref.getScrollSpeed())
                    initPdfViewAndLoad(
                        viewConfigurator,
                        pageNumber,
                        savePassword,
                        cachedCropMargins,
                        hash,
                        loadToken,
                        documentUri,
                        readingDirectionState.effectiveDirection,
                        viewState,
                    )
                }
            }
        }
    }

    private suspend fun resolveReadingDirection(fileHash: String?, documentUri: Uri?): ReadingDirectionLoadState {
        val overrideDirection = fileHash
            ?.let { databaseManager.findReadingDirectionOverride(it) }
            ?.let { ReadingDirection.fromOverrideId(it) }
        val storedDetectedDirection = fileHash
            ?.let { databaseManager.findDetectedReadingDirection(it) }
            ?.let { ReadingDirection.fromId(it) }
        if (overrideDirection != null) {
            return ReadingDirectionLoadState(
                overrideDirection,
                detectedDirection = storedDetectedDirection,
                effectiveDirection = overrideDirection,
            )
        }

        val detectedDirection = storedDetectedDirection ?: detectReadingDirectionIfNeeded(documentUri)

        return ReadingDirectionLoadState(
            overrideDirection,
            detectedDirection,
            ReadingDirection.effective(overrideDirection, detectedDirection),
        )
    }

    private suspend fun detectReadingDirectionIfNeeded(documentUri: Uri?): ReadingDirection? {
        if (!pref.getHorizontalScroll() || documentUri == null) {
            return null
        }
        val result = ReadingDirectionDetector.detect(this, documentUri, pdf.password)
        return result.direction.takeIf { result.cacheable }
    }

    private fun applyTileRenderingPreferences() {
        Constants.THUMBNAIL_RATIO = pref.getThumbnailRation()
        val partSize = pref.getPartSize()
        Constants.PART_SIZE = partSize
        val tilePixels = partSize * partSize
        Constants.Cache.CACHE_SIZE =
            (TILE_CACHE_PIXEL_BUDGET / tilePixels).toInt().coerceIn(MIN_TILE_CACHE_SIZE, MAX_TILE_CACHE_SIZE)
    }

    private fun initPdfViewAndLoad(
        viewConfigurator: Configurator,
        pageNumber: Int,
        savePassword: Boolean,
        cachedCropMargins: CropMargins?,
        fileHash: String?,
        loadToken: Long,
        documentUri: Uri?,
        readingDirection: ReadingDirection = pdf.effectiveReadingDirection,
        viewState: PDFView.ViewState? = null,
        applyDocumentLoadDefaults: Boolean = true,
        zoomDisabled: Boolean = false,
        horizontalSwipeDisabled: Boolean = false,
    ) {
        val pdfView = binding.pdfView
        applyTileRenderingPreferences()
        pdfView.useBestQuality(pref.getHighQuality())
        pdfView.minZoom = Preferences.minZoomDefault
        pdfView.midZoom = Preferences.midZoomDefault
        pdfView.maxZoom = pref.getMaxZoom()
        val spacing = if (pref.getSpaceBetweenPages()) Preferences.spacingDefault else 0

        viewConfigurator   // creates a PDFView.Configurator
            .defaultPage(pageNumber)
            .defaultViewState(viewState)
            .onPageChange { page: Int, pageCount: Int -> setCurrentPage(page, pageCount, fileHash, loadToken, documentUri) }
            .enableAnnotationRendering(Preferences.annotationRenderingDefault)
            .enableAntialiasing(pref.getAntiAliasing())
            .renderDuringScale(true)
            .onDocumentInteraction { motionEvent -> autoScrollManager.handleUserInteraction(motionEvent) }
            .onTap { motionEvent -> handleReaderTap(motionEvent) }
            .onTapUp { motionEvent -> inlineAnnotationActionController.handleImmediatePdfTap(motionEvent) }
            .linkHandler(readerNavigationController.createLinkHandler())
            .scrollHandle(createScrollHandle())
            .spacing(spacing)
            .onError { exception: Throwable ->
                hideProgressBar(loadToken, documentUri)
                handleFileOpeningError(exception)
            }
            .onPageError { page: Int, error: Throwable -> reportLoadPageError(page, error) }
            .pageFitPolicy(FitPolicy.WIDTH)
            .password(pdf.password)
            .swipeHorizontal(pref.getHorizontalScroll())
            .horizontalReadingDirectionRtl(pref.getHorizontalScroll() && readingDirection.isRightToLeft)
            .disableHorizontalSwipe(horizontalSwipeDisabled)
            .zoomDisabled(zoomDisabled)
            .autoSpacing(pref.getHorizontalScroll())
            .pageSnap(pref.getPageSnap())
            .pageFling(pref.getPageFling())
            .nightMode(pdfThemeController.effectivePdfDarkTheme())
            .enableTextSelection(pref.getInlineTextSelection())
            .textSelectionColor(MaterialColors.getColor(binding.root, R.attr.colorPrimary))
            .onTextSelectionChange(object : OnTextSelectionChangeListener {
                override fun onTextSelectionChanged(viewBounds: RectF?, pageIndex: Int) {
                    inlineAnnotationActionController.showSelectionActions(viewBounds)
                }

                override fun onTextSelectionCleared() {
                    inlineAnnotationActionController.hideActions()
                }
            })
            .cropMargins(isCropMarginsEnabled())
            .cachedCropMargins(cachedCropMargins)
            .onLoad { pageCount ->
                if (session.pendingViewState === viewState) {
                    session.pendingViewState = null
                }
                hideProgressBar(loadToken, documentUri)
                pdfThemeController.configureTheme()
                createPdfRecord(savePassword, pdf, fileHash, loadToken, documentUri)
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
            .load()

        // Show the page scroll handler for a while when the pdf is loaded then hide it.
        pdfView.performTap()
    }

    private fun reloadWithCropMargins(
        configurator: Configurator,
        pageNumber: Int,
        cropMargins: CropMargins,
        viewState: PDFView.ViewState?,
    ) {
        val zoomDisabled = binding.pdfView.isZoomDisabled
        val horizontalSwipeDisabled = binding.pdfView.isHorizontalSwipeDisabled
        initPdfViewAndLoad(
            configurator,
            pageNumber,
            savePassword = false,
            cachedCropMargins = cropMargins,
            fileHash = pdf.fileHash,
            loadToken = session.currentLoadToken,
            documentUri = pdf.uri,
            readingDirection = pdf.effectiveReadingDirection,
            viewState = viewState,
            applyDocumentLoadDefaults = false,
            zoomDisabled = zoomDisabled,
            horizontalSwipeDisabled = horizontalSwipeDisabled,
        )
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
            search = { showSearchDialog(this, pdf) },
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

    private fun createPdfRecord(
        savePassword: Boolean,
        pdf: PDF,
        expectedFileHash: String?,
        loadToken: Long,
        documentUri: Uri?,
    ) {
        val password = if (savePassword) pdf.password else null
        lifecycleScope.launch {
            if (!session.isCurrent(loadToken, documentUri)) {
                return@launch
            }

            // cannot use elvis operator ?: with a suspend function, it won't wait
            if (pdf.fileHash == null && expectedFileHash == null) {
                val computedHash = computeHash(this@MainActivity, pdf)
                if (!session.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                pdf.fileHash = computedHash
            }
            if (!session.isCurrent(loadToken, documentUri)) {
                return@launch
            }

            val fileHash = expectedFileHash ?: pdf.fileHash
            if (fileHash == null) {
                Log.e(TAG, "createPdfRecord: Failed to compute fileHash while creating PdfRecord")
                return@launch
            }
            pdf.fileHash = fileHash

            if (databaseManager.hasRecord(fileHash)) {
                if (!session.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                databaseManager.setLastOpened(fileHash, LocalDateTime.now())
                if (!session.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                if (password != null) {
                    databaseManager.setPassword(fileHash, password)
                }
                if (!session.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                pdf.autoScrollSpeed?.let { databaseManager.setAutoScrollSpeed(fileHash, it) }
                if (!session.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                saveReadingDirectionState(fileHash)
                if (!session.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                cropMarginsController.onRecordAvailable(fileHash)
            }
            else {
                if (!session.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                val record = PdfRecord.from(fileHash, this@MainActivity.pdf, password)
                databaseManager.saveRecordInBackground(record)
                if (!session.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                pdf.autoScrollSpeed?.let { databaseManager.setAutoScrollSpeed(fileHash, it) }
                if (!session.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                cropMarginsController.onRecordAvailable(fileHash)
            }
        }
    }

    private suspend fun saveReadingDirectionState(fileHash: String) {
        databaseManager.setReadingDirectionOverride(fileHash, pdf.readingDirectionOverride?.id)
        pdf.detectedReadingDirection?.let {
            databaseManager.setDetectedReadingDirection(fileHash, it.id)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createScrollHandle(): ScrollHandle {
        // hiding the handle if the pdf.length is 1 will happen when pdf.length is set in setPdfLength()
        val handle = DefaultScrollHandle(this, false, pref.getShowScrollHandlePageCount())
        val fullScreenTouchListener = fullScreenOptionsManager.getOnTouchListener()
        handle.setOnTouchListener { view, motionEvent ->
            autoScrollManager.handleUserInteraction(motionEvent)
            fullScreenTouchListener.onTouch(view, motionEvent)
        }
        handle.setOnClickListener { goToPage() }
        return handle
    }

    private fun copyPageText() {
        val pageNumber = pdf.pageNumber
        if (shouldStopExtracting.getOrElse(pageNumber) { false }) {
            return
        }

        var pageText = ""
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                pageText = binding.pdfView.getPageText(pageNumber)
            }
            catch (e: Throwable) {
                Log.e("PDFium", "extractPageText($pageNumber): error while extracting text", e)
                showFailedExtractTextSnackbar(pageNumber)
            }

            withContext(Dispatchers.Main) {
                if (pageText.isEmpty() || pageText.isBlank()) {
                    showNoTextInPageMessage()
                }
                else {
                    showCopyPageTextDialog(this@MainActivity, binding, pageNumber, pageText)
                }
            }
        }
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

    private fun showFailedExtractTextSnackbar(pageNumber: Int) {
        Snackbar.make(binding.root, "Failed to extract text of this file.", Snackbar.LENGTH_SHORT)
            .setAction("Stop this message") { shouldStopExtracting[pageNumber] = true }
            .show()
    }

    private var showNoTextInPage = true
    private fun showNoTextInPageMessage() {
        if (showNoTextInPage) {
            Snackbar.make(binding.root, "Couldn't find text in this page.", Snackbar.LENGTH_LONG).show()
            showNoTextInPage = false
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

    private fun reportLoadPageError(page: Int, error: Throwable) {
        val message = resources.getString(R.string.cannot_load_page) + page + " " + error
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        Log.e(TAG, message)
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
        else {
            Snackbar.make(binding.root, R.string.file_opening_error, Snackbar.LENGTH_LONG).show()
            Log.e(TAG, getString(R.string.file_opening_error), exception)
        }
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
        autoScrollSpeedStore.flushPendingSave()
        super.onStop()
    }

    override fun onDestroy() {
        autoScrollSpeedStore.flushPendingSave()
        if (::cropMarginsController.isInitialized) {
            cropMarginsController.cancel()
        }
        inlineAnnotationActionController.hideActions()
        super.onDestroy()
    }

    fun hideProgressBar() {
        binding.progressBar.visibility = View.GONE
        binding.progressBar.isIndeterminate = true
        binding.progressBar.progress = 0
    }

    private fun hideProgressBar(loadToken: Long, documentUri: Uri?) {
        if (session.isCurrent(loadToken, documentUri)) {
            hideProgressBar()
        }
    }

    fun saveToFileAndDisplay(pdfFileContent: ByteArray?) {
        onlinePdfController.saveToFileAndDisplay(pdfFileContent)
    }

    private fun navToAppSettings() {
        launchers.settings.launch(Intent(this, SettingsActivity::class.java))
    }

    private fun setCurrentPage(
        pageNumber: Int,
        pageCount: Int,
        expectedFileHash: String?,
        loadToken: Long,
        documentUri: Uri?,
    ) {
        if (!session.isCurrent(loadToken, documentUri)) {
            return
        }
        pdf.pageNumber = pageNumber
        setPdfLength(pageCount)
        updateAppTitle()
        binding.pdfView.announceForAccessibility(getString(R.string.page_x_of_y, pageNumber + 1, pageCount))

        lifecycleScope.launch {
            if (!session.isCurrent(loadToken, documentUri)) {
                return@launch
            }
            // cannot use elvis operator ?: with a suspend function, it won't wait
            val hash = pdf.fileHash ?: expectedFileHash ?: computeHash(this@MainActivity, pdf)
            if (!session.isCurrent(loadToken, documentUri)) {
                return@launch
            }
            if (hash != null) {  // Ensure hash is not null
                pdf.fileHash = hash
                databaseManager.setPageNumber(hash, pageNumber)  // Set the page number in the database
            }
            else {
                showFailedToComputeHashError()
            }
        }
    }

    private fun showFailedToComputeHashError() {
        val message = "Can't hash the file! Last visited page won't be remembered in this session."
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        Log.e(TAG, "showFailedToComputeHashError: $message", RuntimeException())
    }

    private fun setPdfLength(pageCount: Int) {
        pdf.initPdfLength(pageCount)
        if (pageCount == 1) {
            fullScreenOptionsManager.permanentlyHidePageHandle()
        }
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

    private fun showAppFeaturesDialogOnFirstRun() {
        if (pref.getShowFeaturesDialog()) {
            Handler(mainLooper).postDelayed({ showAppFeaturesDialog(this) }, 500)
            pref.setShowFeaturesDialog(false)
        }
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

        var selectedOverride = pdf.readingDirectionOverride
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reading_direction)
            .setSingleChoiceItems(
                readingDirectionDialogItems(),
                readingDirectionDialogSelectedIndex(selectedOverride),
            ) { _, which ->
                selectedOverride = readingDirectionOverrideForDialogIndex(which)
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                applyReadingDirectionOverride(selectedOverride)
            }
            .setNegativeButton(android.R.string.cancel, null)
        if (!pref.getHorizontalScroll()) {
            dialogBuilder.setMessage(R.string.reading_direction_message)
        }
        dialogBuilder.show()
    }

    private fun readingDirectionDialogItems(): Array<String> {
        val autoLabel = if (pdf.effectiveReadingDirection.isRightToLeft) {
            R.string.reading_direction_auto_rtl
        } else {
            R.string.reading_direction_auto_ltr
        }
        return arrayOf(
            getString(autoLabel),
            getString(R.string.reading_direction_ltr),
            getString(R.string.reading_direction_rtl),
        )
    }

    private fun readingDirectionDialogSelectedIndex(direction: ReadingDirection?): Int {
        return when (direction) {
            null -> 0
            ReadingDirection.LEFT_TO_RIGHT -> 1
            ReadingDirection.RIGHT_TO_LEFT -> 2
            ReadingDirection.UNKNOWN -> 0
        }
    }

    private fun readingDirectionOverrideForDialogIndex(index: Int): ReadingDirection? {
        return when (index) {
            1 -> ReadingDirection.LEFT_TO_RIGHT
            2 -> ReadingDirection.RIGHT_TO_LEFT
            else -> null
        }
    }

    private fun applyReadingDirectionOverride(direction: ReadingDirection?) {
        val loadToken = session.currentLoadToken
        val documentUri = pdf.uri
        val oldEffectiveDirection = pdf.effectiveReadingDirection
        lifecycleScope.launch {
            val hash = pdf.fileHash ?: computeHash(this@MainActivity, pdf)
            if (!session.isCurrent(loadToken, documentUri)) {
                return@launch
            }
            if (hash == null) {
                showFailedToComputeHashError()
                return@launch
            }

            pdf.fileHash = hash
            databaseManager.setReadingDirectionOverride(hash, direction?.id)
            if (!session.isCurrent(loadToken, documentUri)) {
                return@launch
            }

            val detectedDirection = if (direction == null && pdf.detectedReadingDirection == null) {
                detectReadingDirectionIfNeeded(documentUri)
            } else {
                pdf.detectedReadingDirection
            }
            if (!session.isCurrent(loadToken, documentUri)) {
                return@launch
            }
            detectedDirection?.let { databaseManager.setDetectedReadingDirection(hash, it.id) }

            pdf.readingDirectionOverride = direction
            pdf.detectedReadingDirection = detectedDirection
            pdf.effectiveReadingDirection = ReadingDirection.effective(direction, detectedDirection)
            if (pref.getHorizontalScroll() && pdf.effectiveReadingDirection != oldEffectiveDirection) {
                recreate()
            }
        }
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
            startActivityForResult(it, PDF.startTextActivity)
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
        val inputLayout = layoutInflater.inflate(R.layout.input_layout, null) as TextInputLayout
        inputLayout.hint = getString(R.string.online_pdf_link)
        inputLayout.setStartIconDrawable(R.drawable.ic_link)
        inputLayout.editText?.apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }

        var confirmedHttpLink: String? = null
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.open_online_pdf)
            .setView(inputLayout)
            .setPositiveButton(R.string.open_online_pdf, null)
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .create()

        dialog.setOnShowListener {
            val openButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            openButton.setOnClickListener {
                val link = inputLayout.editText?.text?.toString()?.trim().orEmpty()
                val uri = link.toUri()
                inputLayout.error = null

                if (!uri.scheme.isOnlinePdfScheme() || uri.host.isNullOrBlank()) {
                    confirmedHttpLink = null
                    openButton.setText(R.string.open_online_pdf)
                    inputLayout.error = getString(R.string.invalid_online_pdf_link)
                    return@setOnClickListener
                }

                if (uri.scheme.equals("http", ignoreCase = true) && confirmedHttpLink != link) {
                    confirmedHttpLink = link
                    openButton.setText(R.string.proceed_anyway)
                    inputLayout.error = getString(R.string.http_online_pdf_warning)
                    return@setOnClickListener
                }

                dialog.dismiss()
                runAfterDirtyAnnotationPrompt { displayFromUri(uri, savePassword = true) }
            }
        }
        dialog.show()
    }

    private fun String?.isOnlinePdfScheme(): Boolean {
        return equals("http", ignoreCase = true) || equals("https", ignoreCase = true)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        hideProgressBar()
        readerNavigationController.handleActivityResult(requestCode, resultCode, intent)
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
                if (!pref.getDoubleTapToExitEnabled() || doubleBackToExitPressedOnce) {
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
