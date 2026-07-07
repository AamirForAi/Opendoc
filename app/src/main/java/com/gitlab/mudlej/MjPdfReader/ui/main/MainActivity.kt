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
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.*
import android.print.PrintManager
import android.provider.MediaStore
import android.provider.Settings
import android.text.InputType
import android.text.format.DateFormat
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.databinding.PasswordDialogBinding
import com.gitlab.mudlej.MjPdfReader.enums.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.enums.FileType
import com.gitlab.mudlej.MjPdfReader.enums.ReadingDirection
import com.gitlab.mudlej.MjPdfReader.manager.autoscroll.AutoScrollManager
import com.gitlab.mudlej.MjPdfReader.manager.autoscroll.AutoScrollManagerImpl
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManager
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManagerImpl
import com.gitlab.mudlej.MjPdfReader.manager.fullscreen.FullScreenOptionsManager
import com.gitlab.mudlej.MjPdfReader.manager.fullscreen.FullScreenOptionsManagerImpl
import com.gitlab.mudlej.MjPdfReader.manager.permission.PermissionManager
import com.gitlab.mudlej.MjPdfReader.manager.print.PdfDocumentAdapter
import com.gitlab.mudlej.MjPdfReader.repository.AppDatabase
import com.gitlab.mudlej.MjPdfReader.repository.PdfRecord
import com.gitlab.mudlej.MjPdfReader.ui.*
import com.gitlab.mudlej.MjPdfReader.ui.about.AboutActivity
import com.gitlab.mudlej.MjPdfReader.ui.bookmark.BookmarksActivity
import com.gitlab.mudlej.MjPdfReader.ui.bookmark.BookmarkState
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.link.LinksActivity
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_reader.TextReaderActivity
import com.gitlab.mudlej.MjPdfReader.util.*
import com.gitlab.mudlej.MjPdfReader.util.FileUtil.fileFromUri
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shockwave.pdfium.PdfPasswordException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.time.LocalDateTime
import java.util.*
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private data class RetainedPdfBytes(val uri: String?, val bytes: ByteArray?)

    private data class PendingAutoScrollSpeed(val fileHash: String, val speed: Int)

    private data class ReadingDirectionLoadState(
        val overrideDirection: ReadingDirection?,
        val detectedDirection: ReadingDirection?,
        val effectiveDirection: ReadingDirection,
    )

    private companion object {
        const val AUTO_SCROLL_SPEED_SAVE_DELAY = 300L
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
    private lateinit var annotationSaveController: AnnotationSaveController
    private lateinit var pref: Preferences
    private val pdf = PDF()
    private var documentLoadToken = 0L
    private var cropMarginsEnabledForCurrentDocument = false
    private var activeSearchResultsSnackbar: Snackbar? = null
    private var pendingViewState: PDFView.ViewState? = null
    private var activeSearchResultPageNumber: Int? = null
    private var activeBookmarksSnackbar: Snackbar? = null
    private var bookmarkState = BookmarkState()
    private var autoScrollSpeedSaveJob: Job? = null
    private var pendingAutoScrollSpeedSave: PendingAutoScrollSpeed? = null

    private lateinit var actionBarMenu: Menu

    private val launchers = Launchers(
        Launcher(this, pdf).pdfPicker(),
        Launcher(this, pdf).saveToDownloadPermission(::saveDownloadedFileAfterPermissionRequest),
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
    private var brightness: Int = -1


    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "-----------onCreate: ${pdf.name} ")
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomActionBar()
        ColorUtil.colorize(this, window, supportActionBar)

        // To avoid FileUriExposedException, (https://stackoverflow.com/questions/38200282/)
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())

        // init
        pref = Preferences(PreferenceManager.getDefaultSharedPreferences(this))
        autoScrollManager = AutoScrollManagerImpl(binding, pdf, pref, ::onAutoScrollSpeedChanged)
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
        ) { hideBrightnessControl(binding) }
        shortcutBarController = ShortcutBarController(
            this,
            binding,
            pref,
            actionResolver,
        ) { pdf.isFullScreenToggled }
        databaseManager = DatabaseManagerImpl(AppDatabase.getInstance(applicationContext))
        cropMarginsController = CropMarginsController(
            this,
            binding,
            databaseManager,
            pdf,
            lifecycleScope,
            ::isCropMarginsEnabled,
            ::setCropMarginsEnabled,
            ::isCurrentDocument,
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
        ) {
            updateAppTitle()
        }
        inlineAnnotationActionController = InlineAnnotationActionController(
            this,
            binding,
            ::clearActiveSearchResultHighlight,
            ::onAnnotationEdit,
            ::updateAnnotationSaveUiPosition,
        ) { fullScreenOptionsManager.showAllTemporarilyOrHide() }
        formFieldController = FormFieldController(this, binding, ::onAnnotationEdit)
        permissionManager = PermissionManager(this)
        brightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 2
        inlineAnnotationActionController.configure { annotationSaveController.saveHighlights() }

        applyTileRenderingPreferences()

        // Show Intro Activity and Features Dialog on the first install
        if (pref.getFirstInstall()) {
            onFirstInstall()
            finish()
            return
        }

        // navigate to settings to get permission to manage storage
        //permissionManager.checkStoragePermission { }

        // Create PDF by restoring it in case of an activity restart OR ...
        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState)
        }
        else {
            val intentUri = intent.data
            if (intentUri == null) {
                pickFile()
                //goToHomePage()
            } else {
                prepareNewDocument(intentUri)
            }
        }

        displayFromUri(pdf.uri, true)
        setButtonsFunctionalities()
        showAppFeaturesDialogOnFirstRun()
        overrideOnBackButtonPressed()
    }

    private fun goToHomePage() {
        Intent(this, HomeActivity::class.java).also {
            startActivity(it)
        }
        finish()
    }

    fun initPdf(pdf: PDF, uri: Uri) {
        prepareNewDocument(uri)
        val loadToken = documentLoadToken
        lifecycleScope.launch {
            val hash = computeHash(this@MainActivity, pdf)
            if (isCurrentDocument(loadToken, uri)) {
                pdf.fileHash = hash
            }
        }
    }

    private fun prepareNewDocument(uri: Uri) {
        if (pdf.uri == uri) {
            return
        }
        flushPendingAutoScrollSpeedSave()
        cropMarginsController.cancel()
        documentLoadToken++
        pdf.uri = uri
        pdf.fileHash = null
        pdf.pageNumber = 0
        pdf.zoom = 1F
        pendingViewState = null
        pdf.autoScrollSpeed = null
        pdf.readingDirectionOverride = null
        pdf.detectedReadingDirection = null
        pdf.effectiveReadingDirection = ReadingDirection.LEFT_TO_RIGHT
        cropMarginsEnabledForCurrentDocument = pref.getAlwaysHideMargins()
        shouldStopExtracting.clear()
        showNoTextInPage = true
        resetSearchResultState()
        resetBookmarkState()
        inlineAnnotationActionController.hideActions()
        PdfBytesHolder.clear()
        annotationController.resetForDocument(uri)
        updateAnnotationDirtyUi()
    }

    private fun resetSearchResultState() {
        clearActiveSearchResultHighlight()
        activeSearchResultsSnackbar?.dismiss()
        activeSearchResultsSnackbar = null
    }

    private fun resetBookmarkState() {
        activeBookmarksSnackbar?.dismiss()
        activeBookmarksSnackbar = null
        bookmarkState = BookmarkState()
    }

    private fun isCropMarginsEnabled() = cropMarginsEnabledForCurrentDocument

    private fun setCropMarginsEnabled(enabled: Boolean) {
        cropMarginsEnabledForCurrentDocument = enabled
        refreshConfiguredActions()
    }

    private fun isCurrentDocument(loadToken: Long, uri: Uri?): Boolean {
        return documentLoadToken == loadToken && annotationController.acceptsDocumentUri(uri)
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
                //Toast.makeText(this, title, Toast.LENGTH_LONG).show()
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
            //Toast.makeText(this, R.string.toast_pick_file_error, Toast.LENGTH_LONG).show()
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
        val loadToken = documentLoadToken
        val documentUri = pdf.uri
        val viewState = pendingViewState
        lifecycleScope.launch {
            val hash = pdf.fileHash ?: computeHash(this@MainActivity, pdf)
            if (!isCurrentDocument(loadToken, documentUri)) {
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
            if (!isCurrentDocument(loadToken, documentUri)) {
                return@launch
            }

            val pageNumber = if (pdf.pageNumber == 0 && hash != null) {
                databaseManager.findPageNumber(hash)
            } else {
                pdf.pageNumber
            }
            if (!isCurrentDocument(loadToken, documentUri)) {
                return@launch
            }

            val autoScrollSpeed = hash?.let { databaseManager.findAutoScrollSpeed(it) }
            if (!isCurrentDocument(loadToken, documentUri)) {
                return@launch
            }

            val readingDirectionState = resolveReadingDirection(hash, documentUri)
            if (!isCurrentDocument(loadToken, documentUri)) {
                return@launch
            }

            val cachedCropMargins = cropMarginsController.findCached(hash)
            if (!isCurrentDocument(loadToken, documentUri)) {
                return@launch
            }
            pdf.pageNumber = pageNumber
            pdf.autoScrollSpeed = pdf.autoScrollSpeed ?: autoScrollSpeed
            pdf.readingDirectionOverride = readingDirectionState.overrideDirection
            pdf.detectedReadingDirection = readingDirectionState.detectedDirection
            pdf.effectiveReadingDirection = readingDirectionState.effectiveDirection
            withContext(Dispatchers.Main) {
                if (isCurrentDocument(loadToken, documentUri)) {
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
            .nightMode(effectivePdfDarkTheme())
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
                if (pendingViewState === viewState) {
                    pendingViewState = null
                }
                hideProgressBar(loadToken, documentUri)
                configureTheme()
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
                reapplyFullscreenStateAfterLoad()
                cropMarginsController.startIfNeeded(cachedCropMargins, fileHash, loadToken, documentUri, pageCount)
                maybeRestoreAnnotations(documentUri, loadToken)
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
            loadToken = documentLoadToken,
            documentUri = pdf.uri,
            readingDirection = pdf.effectiveReadingDirection,
            viewState = viewState,
            applyDocumentLoadDefaults = false,
            zoomDisabled = zoomDisabled,
            horizontalSwipeDisabled = horizontalSwipeDisabled,
        )
    }

    private fun openTextModeByDefault() {
        if (pref.getDefaultTextReader()) {
            navToTextReader()
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
            toggleHorizontalLock = { horizontalSwipeButtonListener(binding) },
            readingDirection = ::showReadingDirectionDialog,
            toggleZoomLock = { zoomLockButtonListener(binding) },
            toggleCropMargins = ::toggleCropMargins,
            screenshot = ::takeScreenshot,
            switchTheme = ::switchPdfTheme,
            reload = ::reloadPdf,
            openLocal = ::pickFile,
            openOnline = ::showOpenOnlinePdfDialog,
            search = { showSearchDialog(this, pdf) },
            goToPage = ::goToPage,
            extractText = ::copyPageText,
            textReader = ::navToTextReader,
            share = { shareFile(pdf.uri, FileType.PDF) },
            settings = ::navToAppSettings,
            fileMetadata = ::showFileMetadata,
            about = { startActivity(navIntent(this, AboutActivity::class.java)) },
            tableOfContents = ::showBookmarks,
            linksInFile = ::showLinks,
            print = ::printFile,
        )
    }

    private fun onAutoScrollSpeedChanged(speed: Int) {
        pdf.autoScrollSpeed = speed
        val fileHash = pdf.fileHash ?: return
        val pending = PendingAutoScrollSpeed(fileHash, speed)

        pendingAutoScrollSpeedSave = pending
        autoScrollSpeedSaveJob?.cancel()
        autoScrollSpeedSaveJob = lifecycleScope.launch {
            delay(AUTO_SCROLL_SPEED_SAVE_DELAY)
            savePendingAutoScrollSpeed(pending)
        }
    }

    private suspend fun savePendingAutoScrollSpeed(pending: PendingAutoScrollSpeed) {
        if (pendingAutoScrollSpeedSave != pending) {
            return
        }

        databaseManager.setAutoScrollSpeed(pending.fileHash, pending.speed)
        if (pendingAutoScrollSpeedSave == pending) {
            pendingAutoScrollSpeedSave = null
        }
    }

    private fun flushPendingAutoScrollSpeedSave() {
        val pending = pendingAutoScrollSpeedSave ?: return
        autoScrollSpeedSaveJob?.cancel()
        autoScrollSpeedSaveJob = null
        pendingAutoScrollSpeedSave = null
        backgroundSaveScope.launch {
            databaseManager.setAutoScrollSpeed(pending.fileHash, pending.speed)
        }
    }

    private fun checkAutoFullScreen() {
        if (pref.getAutoFullScreen() && !pdf.isFullScreenToggled) {
            toggleFullscreen()
        }
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
            if (!isCurrentDocument(loadToken, documentUri)) {
                return@launch
            }

            // cannot use elvis operator ?: with a suspend function, it won't wait
            if (pdf.fileHash == null && expectedFileHash == null) {
                val computedHash = computeHash(this@MainActivity, pdf)
                if (!isCurrentDocument(loadToken, documentUri)) {
                    return@launch
                }
                pdf.fileHash = computedHash
            }
            if (!isCurrentDocument(loadToken, documentUri)) {
                return@launch
            }

            val fileHash = expectedFileHash ?: pdf.fileHash
            if (fileHash == null) {
                Log.e(TAG, "createPdfRecord: Failed to compute fileHash while creating PdfRecord")
                return@launch
            }
            pdf.fileHash = fileHash

            if (databaseManager.hasRecord(fileHash)) {
                if (!isCurrentDocument(loadToken, documentUri)) {
                    return@launch
                }
                databaseManager.setLastOpened(fileHash, LocalDateTime.now())
                if (!isCurrentDocument(loadToken, documentUri)) {
                    return@launch
                }
                if (password != null) {
                    databaseManager.setPassword(fileHash, password)
                }
                if (!isCurrentDocument(loadToken, documentUri)) {
                    return@launch
                }
                pdf.autoScrollSpeed?.let { databaseManager.setAutoScrollSpeed(fileHash, it) }
                if (!isCurrentDocument(loadToken, documentUri)) {
                    return@launch
                }
                saveReadingDirectionState(fileHash)
                if (!isCurrentDocument(loadToken, documentUri)) {
                    return@launch
                }
                cropMarginsController.onRecordAvailable(fileHash)
            }
            else {
                if (!isCurrentDocument(loadToken, documentUri)) {
                    return@launch
                }
                val record = PdfRecord.from(fileHash, this@MainActivity.pdf, password)
                databaseManager.saveRecordInBackground(record)
                if (!isCurrentDocument(loadToken, documentUri)) {
                    return@launch
                }
                pdf.autoScrollSpeed?.let { databaseManager.setAutoScrollSpeed(fileHash, it) }
                if (!isCurrentDocument(loadToken, documentUri)) {
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
            if (!hasJournal || !isCurrentDocument(loadToken, uri)) {
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
        if (!isCurrentDocument(loadToken, documentUri)) {
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
        setBrightnessSeekbarListener(binding)
        binding.apply {
            rotateScreenButton.setOnClickListener { rotateScreen() }
            brightnessButton.setOnClickListener { setBrightnessButtonListeners(binding) }
            screenshotButton.setOnClickListener { takeScreenshot() }
            toggleHorizontalSwipeButton.setOnClickListener { horizontalSwipeButtonListener(binding) }
            toggleZoomLockButton.setOnClickListener { zoomLockButtonListener(binding) }
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

    private fun toggleZoomDisabled(binding: ActivityMainBinding) {
        binding.pdfView.isZoomDisabled = !binding.pdfView.isZoomDisabled
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

    private fun zoomLockButtonListener(binding: ActivityMainBinding) {
        binding.apply {
            if (pdfView.isZoomDisabled) {
                enableZooming(binding)
            }
            else {
                disableZooming(binding)
            }
        }
    }

    private fun horizontalSwipeButtonListener(binding: ActivityMainBinding) {
        binding.apply {
            if (pdfView.isHorizontalSwipeDisabled) {
                enableHorizontalSwiping(binding)
            }
            else {
                disableHorizontalSwiping(binding)
            }
        }
    }

    private fun enableZooming(binding: ActivityMainBinding) {
        binding.toggleZoomLockButton.icon = drawableOf(R.drawable.ic_zoom_out)
        binding.pdfView.isZoomDisabled = false
    }

    private fun disableZooming(binding: ActivityMainBinding) {
        binding.toggleZoomLockButton.icon = drawableOf(R.drawable.ic_lock)
        binding.pdfView.isZoomDisabled = true
    }

    private fun enableHorizontalSwiping(binding: ActivityMainBinding) {
        binding.toggleHorizontalSwipeButton.icon = drawableOf(R.drawable.ic_allow_horizontal_swipe)
        binding.pdfView.isHorizontalSwipeDisabled = false
    }

    private fun disableHorizontalSwiping(binding: ActivityMainBinding) {
        binding.toggleHorizontalSwipeButton.icon = drawableOf(R.drawable.ic_horizontal_swipe_locked)
        binding.pdfView.isHorizontalSwipeDisabled = true
    }

    private fun setBrightnessButtonListeners(binding: ActivityMainBinding) {
        if (binding.brightnessLayout.isVisible) hideBrightnessControl(binding) else showBrightnessControl(binding)
    }

    private fun hideBrightnessControl(binding: ActivityMainBinding) {
        binding.brightnessLayout.visibility = View.GONE
        pdf.isBrightnessClicked = false
    }

    private fun showBrightnessControl(binding: ActivityMainBinding) {
        binding.brightnessLayout.visibility = View.VISIBLE
        pdf.isBrightnessClicked = true
    }

    private fun setBrightnessSeekbarListener(binding: ActivityMainBinding) {
        // init the seekbar
        val brightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        binding.brightnessSeekBar.progress = brightness
        binding.brightnessPercentage.text = "$brightness%"
        binding.brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (seekBar == null) return
                // Don't override system's brightness if the user didn't manually asked for it
                if (fromUser) updateBrightness(progress)
            }
        })

    }

    private fun updateBrightness(brightness: Int) {
        binding.brightnessPercentage.text = "$brightness%"
        window.attributes.screenBrightness = brightness.toFloat() / 100
        window.attributes = window.attributes // apply it
    }

    private fun exitFullScreenListener(binding: ActivityMainBinding) {
        binding.exitFullScreenButton.setOnClickListener { exitFullscreen() }
    }

    private fun exitFullscreen() {
        if (!pref.getAlwaysHorizontal()) {
            unlockScreenOrientation()
        }
        toggleFullscreen()
        autoScrollManager.stop()
        enableZooming(binding)
        hideBrightnessControl(binding)
        autoScrollManager.hideControls()
        enableHorizontalSwiping(binding)

        // A try to give the brightness control back to the system but this won't work
        // updateBrightness(brightness)
    }

    private fun unlockScreenOrientation() {
        // set orientation to unspecified so that the screen rotation will be unlocked
        // this is because PORTRAIT / LANDSCAPE modes will lock the app in them
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pdf.isFullScreenToggled) {
            ColorUtil.enterFullscreen(window)
        }
    }

    public override fun onResume() {
        Log.i(TAG, "-----------onResume: ${pdf.name} ")
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
        // if (pdf.uri == null) return

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
        if (pdf.isFullScreenToggled) {
            pdf.isFullScreenToggled = false
            toggleFullscreen()
        }
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
            //Toast.makeText(this, "Error sharing the file. (${e.message})", Toast.LENGTH_LONG).show()
            Snackbar.make(binding.root, "Error sharing the file. (${e.message})", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun configureTheme() {
        ColorUtil.colorize(this, window, supportActionBar)
        val color = ColorUtil.getBarColor(this)
        binding.secondBarScrollView.setBackgroundColor(color)

        val pdfView = binding.pdfView

        applyPdfThemeToView(effectivePdfDarkTheme(), reloadPages = false)

        val appNightMode = when (pref.getInterfaceTheme()) {
            Preferences.themeSystem -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            Preferences.themeDark -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        if (AppCompatDelegate.getDefaultNightMode() != appNightMode) {
            AppCompatDelegate.setDefaultNightMode(appNightMode)
        }
    }

    private fun effectivePdfDarkTheme(): Boolean {
        return when (pref.getPdfPagesTheme()) {
            Preferences.themeSystem -> isSystemDarkTheme()
            Preferences.themeDark -> true
            else -> false
        }
    }

    private fun isSystemDarkTheme(): Boolean {
        return when (applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    private fun reportLoadPageError(page: Int, error: Throwable) {
        val message = resources.getString(R.string.cannot_load_page) + page + " " + error
        //Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        Log.e(TAG, message)
    }

    private fun handleFileOpeningError(exception: Throwable) {
        val fileHash = pdf.fileHash
        if (exception is PdfPasswordException && fileHash != null) {
            if (pdf.password != null) {
                //Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show()
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
            //Toast.makeText(this, R.string.file_opening_error, Toast.LENGTH_LONG).show()
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
            //Toast.makeText(this, R.string.file_opening_error, Toast.LENGTH_LONG).show()
            Snackbar.make(binding.root, R.string.file_opening_error, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showSystemUi() {
        ColorUtil.exitFullscreen(this, window, supportActionBar)
        supportActionBar?.show()
        binding.appBarBottomShadow.visibility = View.VISIBLE
        if (pref.getSecondBarEnabled()) {
            shortcutBarController.updateVisibility()
        }
    }

    private fun hideSystemUi() {
        supportActionBar?.hide()
        binding.appBarBottomShadow.visibility = View.GONE
        binding.secondBarScrollView.visibility = View.GONE
        ColorUtil.enterFullscreen(window)
    }

    private fun toggleFullscreen() {
        if (!pdf.isFullScreenToggled) {
            hideSystemUi()
            pdf.isFullScreenToggled = true
            fullScreenOptionsManager.hideAll()

            // show how to exit Full Screen dialog
            if (pref.getShowFeaturesDialog()) {
                showHowToExitFullscreenDialog(this, pref)
            }
        }
        else {
            pdf.isFullScreenToggled = false
            showSystemUi()
            fullScreenOptionsManager.showAllTemporarilyOrHide()
        }
    }

    private fun reapplyFullscreenStateAfterLoad() {
        if (pdf.isFullScreenToggled) {
            hideSystemUi()
        }
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
                documentLoadToken,
                pdf.uri,
                binding.pdfView.pageCount,
            )
        } else {
            cropMarginsController.cancel()
            recreate()
        }
    }

    private fun downloadOrShowDownloadedFile(uri: Uri) {
        if (PdfBytesHolder.pdfByte == null) {
            val retained = lastCustomNonConfigurationInstance as? RetainedPdfBytes
            if (retained?.uri == uri.toString()) {
                PdfBytesHolder.set(retained.uri, retained.bytes)
            }
        }
        if (PdfBytesHolder.pdfByte != null && PdfBytesHolder.uri != uri.toString()) {
            PdfBytesHolder.clear()
        }
        if (PdfBytesHolder.pdfByte != null) {
            initPdfViewAndLoad(binding.pdfView.fromBytes(PdfBytesHolder.pdfByte))
        }
        else {
            // we will get the pdf asynchronously with the DownloadPDFFile object
            binding.progressBar.isIndeterminate = true
            binding.progressBar.progress = 0
            binding.progressBar.visibility = View.VISIBLE
            val downloadPDFFile = DownloadPDFFile(this, binding, uri.toString())
            downloadPDFFile.execute(uri.toString())
        }
    }

    override fun onRetainCustomNonConfigurationInstance(): Any? {
        return RetainedPdfBytes(PdfBytesHolder.uri, PdfBytesHolder.pdfByte)
    }

    override fun onStop() {
        flushPendingAutoScrollSpeedSave()
        super.onStop()
    }

    override fun onDestroy() {
        flushPendingAutoScrollSpeedSave()
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
        if (isCurrentDocument(loadToken, documentUri)) {
            hideProgressBar()
        }
    }

    fun saveToFileAndDisplay(pdfFileContent: ByteArray?) {
        Log.d(TAG, "saveToFileAndDisplay pdfFileContent is set to: $pdfFileContent: ")
        PdfBytesHolder.set(pdf.uri?.toString(), pdfFileContent)
        saveToDownloadFolderIfAllowed(pdfFileContent)
        initPdfViewAndLoad(binding.pdfView.fromBytes(pdfFileContent))
    }

    private fun saveToDownloadFolderIfAllowed(fileContent: ByteArray?) {
        if (canWriteToDownloadFolder(this)) {
            trySaveToDownloads(fileContent, false)
        }
        else {
            launchers.saveToDownloadPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun trySaveToDownloads(fileContent: ByteArray?, showSuccessMessage: Boolean) {
        try {
            val downloadDirectory =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            writeBytesToFile(downloadDirectory, pdf.name, fileContent)
            if (showSuccessMessage) {
                //Toast.makeText(this, R.string.saved_to_download, Toast.LENGTH_SHORT).show()
                Snackbar.make(binding.root, R.string.saved_to_download, Snackbar.LENGTH_SHORT).show()
            }
        }
        catch (e: IOException) {
            Log.e(TAG, getString(R.string.save_to_download_failed), e)
            //Toast.makeText(this, R.string.save_to_download_failed, Toast.LENGTH_SHORT).show()
            Snackbar.make(binding.root, R.string.save_to_download_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun saveDownloadedFileAfterPermissionRequest(isPermissionGranted: Boolean) {
        if (isPermissionGranted) {
            val bytes = if (PdfBytesHolder.uri == pdf.uri?.toString()) PdfBytesHolder.pdfByte else null
            if (bytes != null) {
                trySaveToDownloads(bytes, true)
            } else {
                Snackbar.make(binding.root, R.string.save_to_download_failed, Snackbar.LENGTH_SHORT).show()
            }
        }
        else {
            //Toast.makeText(this, R.string.save_to_download_failed, Toast.LENGTH_SHORT).show()
            Snackbar.make(binding.root, R.string.save_to_download_failed, Snackbar.LENGTH_SHORT).show()
        }
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
        if (!isCurrentDocument(loadToken, documentUri)) {
            return
        }
        pdf.pageNumber = pageNumber
        setPdfLength(pageCount)
        updateAppTitle()

        lifecycleScope.launch {
            if (!isCurrentDocument(loadToken, documentUri)) {
                return@launch
            }
            // cannot use elvis operator ?: with a suspend function, it won't wait
            val hash = pdf.fileHash ?: expectedFileHash ?: computeHash(this@MainActivity, pdf)
            if (!isCurrentDocument(loadToken, documentUri)) {
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
        val documentUri = pdf.uri
        val bytes = if (PdfBytesHolder.uri == documentUri?.toString()) PdfBytesHolder.pdfByte else null
        if (bytes == null) {
            printUri(documentUri)
            return
        }
        lifecycleScope.launch {
            val tempUri = withContext(Dispatchers.IO) {
                runCatching {
                    val tempFile = File(cacheDir, "print_temp.pdf")
                    tempFile.writeBytes(bytes)
                    Uri.fromFile(tempFile)
                }.getOrNull()
            }
            printUri(tempUri ?: documentUri)
        }
    }

    private fun printUri(uri: Uri?) {
        val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        try {
            printManager.print(
                pdf.name,
                PdfDocumentAdapter(this, uri), null
            )
        }
        catch (e: Throwable) {
            Snackbar.make(binding.root, "Failed to print. Error message: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
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
        Intent(this@MainActivity, LinksActivity::class.java).also { linksIntent ->
            linksIntent.putExtra(PDF.filePathKey, pdf.uri.toString())
            linksIntent.putExtra(PDF.passwordKey, pdf.password)
            startActivityForResult(linksIntent, PDF.startLinksActivity)
        }
    }

    private fun showBookmarks() {
        Intent(this@MainActivity, BookmarksActivity::class.java).also { bookmarkIntent ->
            bookmarkIntent.putExtra(PDF.filePathKey, pdf.uri.toString())
            bookmarkIntent.putExtra(PDF.passwordKey, pdf.password)
            bookmarkState.putInto(bookmarkIntent)
            startActivityForResult(bookmarkIntent, PDF.startBookmarksActivity)
        }
    }

    private fun saveBookmarkState(intent: Intent?) {
        if (intent == null) return

        bookmarkState = BookmarkState.from(intent)
    }

    private fun showBookmarkNavigationSnackbar() {
        resetSearchResultState()
        activeBookmarksSnackbar?.dismiss()

        val snackbar = Snackbar.make(binding.root, getString(R.string.back_to_table_of_contents), Snackbar.LENGTH_INDEFINITE)
        activeBookmarksSnackbar = snackbar
        snackbar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                if (activeBookmarksSnackbar === transientBottomBar) {
                    activeBookmarksSnackbar = null
                }
            }
        })
        snackbar.setAction(getString(R.string.done)) {
            snackbar.dismiss()
        }
        setSnackbarTextAction(snackbar) {
            snackbar.dismiss()
            showBookmarks()
        }
        snackbar.show()
    }

    private fun setSnackbarTextAction(snackbar: Snackbar, onClick: () -> Unit) {
        val snackbarView = snackbar.view
        val textView = snackbarView.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
        textView.setTextColor(MaterialColors.getColor(snackbarView, com.google.android.material.R.attr.colorPrimaryInverse))
        textView.setOnClickListener { onClick() }
    }

    private fun clearActiveSearchResultHighlight() {
        activeSearchResultPageNumber?.let { pageNumber ->
            binding.pdfView.clearSearchResultsHighlight(pageNumber)
            activeSearchResultPageNumber = null
        }
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
        val loadToken = documentLoadToken
        val documentUri = pdf.uri
        val oldEffectiveDirection = pdf.effectiveReadingDirection
        lifecycleScope.launch {
            val hash = pdf.fileHash ?: computeHash(this@MainActivity, pdf)
            if (!isCurrentDocument(loadToken, documentUri)) {
                return@launch
            }
            if (hash == null) {
                showFailedToComputeHashError()
                return@launch
            }

            pdf.fileHash = hash
            databaseManager.setReadingDirectionOverride(hash, direction?.id)
            if (!isCurrentDocument(loadToken, documentUri)) {
                return@launch
            }

            val detectedDirection = if (direction == null && pdf.detectedReadingDirection == null) {
                detectReadingDirectionIfNeeded(documentUri)
            } else {
                pdf.detectedReadingDirection
            }
            if (!isCurrentDocument(loadToken, documentUri)) {
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
        if (pref.getTurnPageByVolumeButtons()) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    binding.pdfView.jumpTo(pdf.pageNumber + 1)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    binding.pdfView.jumpTo(pdf.pageNumber - 1)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun navToTextReader() {
        if (!checkHasFile()) {
            return
        }

        val currentPageIndex = currentPdfViewPageIndex()
        pdf.pageNumber = currentPageIndex
        Intent(this, TextReaderActivity::class.java).also {
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
        showReaderActionsDialog(this, readerActions())
    }

    private fun readerActions(): List<ReaderAction> {
        val hasFile = pdf.hasFile()
        return listOfNotNull(
            readerAction(ConfigurableAction.SWITCH_THEME),
            readerAction(ConfigurableAction.OPEN_LOCAL),
            readerAction(ConfigurableAction.OPEN_ONLINE),
            readerAction(ConfigurableAction.TABLE_OF_CONTENTS),
            readerAction(ConfigurableAction.FULLSCREEN),
            readerAction(ConfigurableAction.SEARCH),
            readerAction(ConfigurableAction.GO_TO_PAGE),
            readerAction(ConfigurableAction.READING_DIRECTION),
            readerAction(ConfigurableAction.CROP_MARGINS),
            readerAction(ConfigurableAction.EXTRACT_TEXT),
            readerAction(ConfigurableAction.SETTINGS),
            ReaderAction(R.string.toggle_shortcuts, R.drawable.ic_awesome, visible = hasFile) {
                toggleSecondBar()
            },
            readerAction(ConfigurableAction.TEXT_READER),
            readerAction(ConfigurableAction.LINKS_IN_FILE),
            readerAction(ConfigurableAction.SHARE),
            readerAction(ConfigurableAction.PRINT),
            readerAction(ConfigurableAction.FILE_METADATA),
            readerAction(ConfigurableAction.ABOUT),
        )
    }

    private fun readerAction(action: ConfigurableAction): ReaderAction? {
        val configuredAction = actionResolver.action(action) ?: return null
        return ReaderAction(
            configuredAction.titleRes,
            configuredAction.iconRes,
            visible = configuredAction.visible,
        ) {
            configuredAction.run()
        }
    }

    private fun showFileMetadata() {
        if (!checkHasFile()) {
            return
        }

        val uri = pdf.uri
        var file: File? = null
        if (uri != null) {
            try {
                file = fileFromUri(this@MainActivity, uri, pdf.name)
            }
            catch (throwable: Throwable) {
                Log.e(TAG, "showFileMetadata: Failed to createFileFromUri", throwable)
            }
        }
        showMetaDialog(this, binding.pdfView.documentMeta, file)
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
        if (pref.getPdfPagesTheme() == Preferences.themeSystem) {
            Snackbar.make(
                binding.root,
                getString(R.string.pdf_theme_follows_system),
                Snackbar.LENGTH_LONG
            ).show()
        }
        else if (checkHasFile()) {
            setPdfTheme(!pref.getPdfDarkTheme())
        }
    }

    private fun setPdfTheme(darkTheme: Boolean) {
        if (pref.getPdfPagesTheme() != Preferences.themeSystem && pref.getPdfDarkTheme() == darkTheme) {
            return
        }
        pref.setPdfPagesTheme(if (darkTheme) Preferences.themeDark else Preferences.themeLight)
        applyPdfThemeToView(darkTheme, reloadPages = true)
    }

    private fun applyPdfThemeToView(darkTheme: Boolean, reloadPages: Boolean) {
        binding.pdfView.setNightMode(darkTheme)
        if (!darkTheme) {
            binding.pdfView.setBackgroundColor(Preferences.pdfDarkBackgroundColor)
        } else {
            binding.pdfView.setBackgroundColor(Preferences.pdfLightBackgroundColor)
        }
        if (reloadPages) {
            binding.pdfView.reloadPages()
        }
    }

    private fun screenShot(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun takeScreenshot() {
        val now = DateFormat.format("yyyy_MM_dd-hh_mm_ss", Date())
        try {
            val fileName = "${pdf.name.removeSuffix(".pdf")} - ${now}.jpg"
            val imageFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)

            fullScreenOptionsManager.showAllTemporarilyOrHide()
            val bitmap = screenShot(binding.pdfView)

            val outputStream = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, PDF.SCREENSHOT_IMAGE_QUALITY, outputStream)
            outputStream.flush()
            outputStream.close()

            val uri = saveImage(bitmap, fileName)
            Snackbar.make(binding.root, getString(R.string.screenshot_saved), Snackbar.LENGTH_SHORT).also {
                it.setAction(getString(R.string.share)) { shareFile(uri, FileType.IMAGE) }
                it.show()
            }
        }
        catch (e: Throwable) {
            // Several error may come out with file handling or DOM
            //Toast.makeText(this, getString(R.string.failed_save_screenshot), Toast.LENGTH_LONG).show()
            Snackbar.make(binding.root, getString(R.string.failed_save_screenshot), Snackbar.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun saveImage(bitmap: Bitmap, fileName: String): Uri? {
        val (fileOutputStream: OutputStream?, imageUri: Uri?) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/*")

            // e.g.     ~/Pictures/app_name/screenshot1.jpg
            contentValues.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/${getString(R.string.mj_app_name)}/"
            )

            val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            Pair(imageUri?.let { contentResolver.openOutputStream(it) }, imageUri)
        }
        else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
            val image = File(imagesDir, fileName)
            Pair(FileOutputStream(image), image.toUri())
        }
        if (fileOutputStream != null) {
            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                PDF.SCREENSHOT_IMAGE_QUALITY,
                fileOutputStream
            )
        }
        fileOutputStream?.close()
        return imageUri
    }

    private fun drawableOf(id: Int): Drawable? {
        return AppCompatResources.getDrawable(this, id)
    }

    private fun saveViewState(outState: Bundle): PDFView.ViewState? {
        val viewState = binding.pdfView.captureViewState() ?: pendingViewState ?: return null
        outState.putBoolean(PDF.viewStateSavedKey, true)
        outState.putFloat(PDF.viewStateZoomKey, viewState.zoom)
        outState.putInt(PDF.viewStatePageIndexKey, viewState.pageIndex)
        outState.putBoolean(PDF.viewStateSwipeVerticalKey, viewState.swipeVertical)
        outState.putBoolean(PDF.viewStateHorizontalReadingDirectionRtlKey, viewState.horizontalReadingDirectionRtl)
        outState.putFloat(PDF.viewStateRelativeCrossAxisCenterKey, viewState.relativeCrossAxisCenter)
        outState.putFloat(PDF.viewStatePageCenterOffsetRatioKey, viewState.pageCenterOffsetRatio)
        return viewState
    }

    private fun restoreViewState(savedState: Bundle): PDFView.ViewState? {
        if (!savedState.getBoolean(PDF.viewStateSavedKey, false)) {
            return null
        }

        return PDFView.ViewState(
            savedState.getFloat(PDF.viewStateZoomKey, 1f),
            savedState.getInt(PDF.viewStatePageIndexKey, 0),
            savedState.getBoolean(PDF.viewStateSwipeVerticalKey, true),
            savedState.getBoolean(PDF.viewStateHorizontalReadingDirectionRtlKey, false),
            savedState.getFloat(PDF.viewStateRelativeCrossAxisCenterKey, 0.5f),
            savedState.getFloat(PDF.viewStatePageCenterOffsetRatioKey, 0.5f),
        )
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
        val viewState = saveViewState(outState)
        outState.putFloat(PDF.zoomKey, viewState?.zoom ?: pdf.zoom)
        outState.putBoolean(PDF.isExtractingTextFinishedKey, pdf.isExtractingTextFinished)
        bookmarkState.putInto(outState)
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
        cropMarginsEnabledForCurrentDocument = savedState.getBoolean(PDF.cropMarginsEnabledKey, pref.getAlwaysHideMargins())
        pendingViewState = restoreViewState(savedState)
        pdf.zoom = pendingViewState?.zoom ?: savedState.getFloat(PDF.zoomKey, 1f)
        pdf.isExtractingTextFinished = savedState.getBoolean(PDF.isExtractingTextFinishedKey)
        bookmarkState = BookmarkState.from(savedState)
        annotationController.resetForDocument(pdf.uri)
        annotationController.restoreSessionOwnedKeys(
            savedState.getStringArrayList(PDF.sessionOwnedAnnotationKeysKey),
        )
        if (savedState.getBoolean(PDF.hasUnsavedAnnotationsKey, false)) {
            annotationController.markDirty()
            annotationController.markSessionOwned(pdf.uri)
        }
        updateAnnotationDirtyUi()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        hideProgressBar()
        when (requestCode) {
            PDF.startBookmarksActivity -> {
                saveBookmarkState(intent)
                if (resultCode == PDF.BOOKMARK_RESULT_OK) {
                    val pageIndex = intent?.getIntExtra(PDF.chosenBookmarkKey, pdf.pageNumber) ?: return
                    binding.pdfView.jumpTo(pageIndex)
                    showBookmarkNavigationSnackbar()
                }
            }
            PDF.startTextActivity -> {
                if (resultCode == RESULT_OK) {
                    val pageIndex = intent?.getIntExtra(PDF.pageNumberKey, pdf.pageNumber) ?: return
                    val pageCount = binding.pdfView.pageCount
                    val boundedPageIndex = if (pageCount > 0) pageIndex.coerceIn(0, pageCount - 1) else pageIndex.coerceAtLeast(0)
                    pdf.pageNumber = boundedPageIndex
                    updateAppTitle()
                    binding.pdfView.jumpTo(boundedPageIndex)
                }
            }
            PDF.startLinksActivity -> {
                if (resultCode == PDF.LINK_RESULT_OK) {
                    val pageNumber = intent?.getIntExtra(PDF.linkResultKey, pdf.pageNumber) ?: return
                    val pageIndex = pageNumber - 1
                    binding.pdfView.jumpTo(pageIndex)
                }
            }
            PDF.startSearchActivity -> {
                if (resultCode == PDF.SEARCH_RESULT_OK) {
                    val searchResultJson = intent?.getStringExtra(PDF.searchResultKey) ?: return
                    val searchResultType = object : TypeToken<SearchResult>() {}.type
                    val searchResult = Gson().fromJson<SearchResult>(searchResultJson, searchResultType)

                    clearActiveSearchResultHighlight()
                    activeSearchResultsSnackbar?.dismiss()

                    // highlight the result text
                    val textBound = binding.pdfView.createHighlightText(
                        searchResult.pageNumber,
                        searchResult.originalIndex,
                        searchResult.inputEnd - searchResult.inputStart,
                        true
                    )

                    if (textBound.isEmpty()) {
                        Snackbar.make(binding.root, "Failed to highlight search result", Snackbar.LENGTH_SHORT).show()
                    }
                    // I disabled this because I couldn't get the zooming in to work properly in all cases, it is ~80% of the time correct.
                    // else if (textBound.size == 1) {
                    //     binding.pdfView.zoomWithAnimation(textBound[0].toRectF(), 3f, searchResult.pageNumber)
                    //     binding.pdfView.reloadPages()    // to show the highlighting
                    //}
                    else {
                        activeSearchResultPageNumber = searchResult.pageNumber
                        // because the user may not see the highlight if it was zoomed in before searching
                        binding.pdfView.resetZoomWithAnimation()
                        binding.pdfView.reloadPages()   // to show the highlighting
                    }

                    // show a snackbar with a button that will remove the highlight (it wills still be cached for a bit)
                    val snackbar = Snackbar.make(binding.root, getString(R.string.results), Snackbar.LENGTH_INDEFINITE)
                    activeSearchResultsSnackbar = snackbar
                    snackbar.addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            if (activeSearchResultsSnackbar === transientBottomBar) {
                                activeSearchResultsSnackbar = null
                            }
                        }
                    })
                    snackbar.setAction(getString(R.string.done)) {
                        clearActiveSearchResultHighlight()
                        snackbar.dismiss()
                    }
                    setSnackbarTextAction(snackbar) {
                        //binding.pdfView.resetZoomWithAnimation()
                        //Handler(Looper.getMainLooper()).postDelayed({
                        Intent(this@MainActivity, SearchActivity::class.java).also { searchIntent ->
                            searchIntent.putExtra(PDF.filePathKey, pdf.uri.toString())
                            searchIntent.putExtra(PDF.passwordKey, pdf.password)
                            pdf.fileHash?.let { searchIntent.putExtra(PDF.fileHashKey, it) }
                            pdf.lastQuery?.let { searchIntent.putExtra(PDF.searchQueryKey, it.trim()) }
                            searchIntent.putExtra(PDF.resultPositionInListKey, searchResult.searchResultIndexInList)
                            startActivityForResult(searchIntent, PDF.startSearchActivity)
                        }
                        //}, 400) // same as zoom-out animation duration (not a good way to do it, I know)
                    }
                    snackbar.show()

                    binding.pdfView.jumpUsingPageNumber(searchResult.pageNumber)
                }
            }
        }
    }


    private fun overrideOnBackButtonPressed() {
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("BackPress", "onBackPressed called: doubleBackToExitPressedOnce = $doubleBackToExitPressedOnce")
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
                        Log.d("BackPress", "Coroutine executing: resetting doubleBackToExitPressedOnce")
                        doubleBackToExitPressedOnce = false
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

}
