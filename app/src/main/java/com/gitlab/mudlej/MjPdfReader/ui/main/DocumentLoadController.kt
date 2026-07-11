package com.gitlab.mudlej.MjPdfReader.ui.main

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import android.view.MotionEvent
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.PDFView.Configurator
import com.github.barteksc.pdfviewer.listener.OnTextSelectionChangeListener
import com.github.barteksc.pdfviewer.model.CropMargins
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.scroll.ScrollHandle
import com.github.barteksc.pdfviewer.util.Constants
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.DocumentState
import com.gitlab.mudlej.MjPdfReader.data.PdfBytesHolder
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.enums.ReadingDirection
import com.gitlab.mudlej.MjPdfReader.manager.autoscroll.AutoScrollManager
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManager
import com.gitlab.mudlej.MjPdfReader.manager.fullscreen.FullScreenOptionsManager
import com.gitlab.mudlej.MjPdfReader.repository.PdfRecord
import com.gitlab.mudlej.MjPdfReader.util.UriCanonicalizer
import com.gitlab.mudlej.MjPdfReader.util.computeHash
import com.gitlab.mudlej.MjPdfReader.util.getFileName
import com.google.android.material.color.MaterialColors
import com.gitlab.mudlej.MjPdfReader.util.AppSnackbar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class DocumentLoadController(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val pdf: DocumentState,
    private val vm: ReaderViewModel,
    private val pref: Preferences,
    private val databaseManager: DatabaseManager,
    private val readingDirectionResolver: ReadingDirectionResolver,
    private val scope: CoroutineScope,
    private val annotationSaveController: AnnotationSaveController,
    private val cropMarginsController: CropMarginsController,
    private val autoScrollManager: AutoScrollManager,
    private val pdfThemeController: PdfThemeController,
    private val readerNavigationController: ReaderNavigationController,
    private val fullScreenOptionsManager: FullScreenOptionsManager,
    private val inlineAnnotationActionController: InlineAnnotationActionController,
    private val prepareNewDocument: (Uri) -> Unit,
    private val updateActionBarButtons: () -> Unit,
    private val updateAppTitle: () -> Unit,
    private val downloadOrShowDownloadedFile: (Uri) -> Unit,
    private val handleReaderTap: (MotionEvent) -> Boolean,
    private val goToPage: () -> Unit,
    private val hideProgressBarNow: () -> Unit,
    private val handleOpenError: (Throwable) -> Unit,
    private val onDocumentLoaded: (
        pageCount: Int,
        cachedCropMargins: CropMargins?,
        fileHash: String?,
        loadToken: Long,
        documentUri: Uri?,
        applyDocumentLoadDefaults: Boolean,
    ) -> Unit,
) {

    fun initPdf(pdf: DocumentState, uri: Uri) {
        prepareNewDocument(uri)
        val loadToken = vm.currentLoadToken
        scope.launch {
            val hash = computeHash(activity, pdf)
            if (vm.isCurrent(loadToken, uri)) {
                pdf.fileHash = hash
            }
        }
    }

    fun displayFromUri(uri: Uri?, savePassword: Boolean = false) {
        if (uri == null) {
            updateActionBarButtons()
            return
        }

        prepareNewDocument(uri)

        pdf.name = getFileName(activity, uri)
        updateActionBarButtons()
        updateAppTitle()
        pdf.resetLength()

        activity.setTaskDescription(ActivityManager.TaskDescription(pdf.name))
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

    fun loadCurrentDocument(savePassword: Boolean = false) {
        val uri = pdf.uri ?: return
        val bytes = PdfBytesHolder.bytesFor(uri.toString())
        val configurator = if (bytes != null) {
            binding.pdfView.fromBytes(bytes)
        } else {
            binding.pdfView.fromUri(uri)
        }
        initPdfViewAndLoad(configurator, savePassword = savePassword)
    }

    fun initPdfViewAndLoad(viewConfigurator: Configurator, savePassword: Boolean = false) {
        val loadToken = vm.currentLoadToken
        val documentUri = pdf.uri
        val viewState = vm.pendingViewState
        scope.launch {
            val hash = pdf.fileHash ?: computeHash(activity, pdf)
            if (!vm.isCurrent(loadToken, documentUri)) {
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
            if (!vm.isCurrent(loadToken, documentUri)) {
                return@launch
            }

            val pageNumber = if (pdf.pageNumber == 0 && hash != null) {
                databaseManager.findPageNumber(hash)
            } else {
                pdf.pageNumber
            }
            if (!vm.isCurrent(loadToken, documentUri)) {
                return@launch
            }

            val autoScrollSpeed = hash?.let { databaseManager.findAutoScrollSpeed(it) }
            if (!vm.isCurrent(loadToken, documentUri)) {
                return@launch
            }

            val readingDirectionState = readingDirectionResolver.resolve(hash, documentUri)
            if (!vm.isCurrent(loadToken, documentUri)) {
                return@launch
            }

            val cachedCropMargins = cropMarginsController.findCached(hash)
            if (!vm.isCurrent(loadToken, documentUri)) {
                return@launch
            }
            vm.setPage(pageNumber)
            pdf.autoScrollSpeed = pdf.autoScrollSpeed ?: autoScrollSpeed
            pdf.readingDirectionOverride = readingDirectionState.overrideDirection
            pdf.detectedReadingDirection = readingDirectionState.detectedDirection
            pdf.effectiveReadingDirection = readingDirectionState.effectiveDirection
            withContext(Dispatchers.Main) {
                if (vm.isCurrent(loadToken, documentUri)) {
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

    fun reloadWithCropMargins(
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
            loadToken = vm.currentLoadToken,
            documentUri = pdf.uri,
            readingDirection = pdf.effectiveReadingDirection,
            viewState = viewState,
            applyDocumentLoadDefaults = false,
            zoomDisabled = zoomDisabled,
            horizontalSwipeDisabled = horizontalSwipeDisabled,
        )
    }

    fun applyTileRenderingPreferences() {
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
        val browserScrollMode = pref.getBrowserScrollMode() && !pref.getHorizontalScroll()

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
                handleOpenError(exception)
            }
            .onPageError { page: Int, error: Throwable -> reportLoadPageError(page, error) }
            .pageFitPolicy(FitPolicy.WIDTH)
            .password(pdf.password)
            .swipeHorizontal(pref.getHorizontalScroll())
            .horizontalReadingDirectionRtl(pref.getHorizontalScroll() && readingDirection.isRightToLeft)
            .disableHorizontalSwipe(horizontalSwipeDisabled)
            .zoomDisabled(zoomDisabled)
            .autoSpacing(pref.getHorizontalScroll())
            .pageSnap(pref.getPageSnap() && !browserScrollMode)
            .pageFling(pref.getPageFling() && !browserScrollMode)
            .freeScrollMode(browserScrollMode)
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
            .cropMargins(vm.cropMarginsEnabled)
            .cachedCropMargins(cachedCropMargins)
            .onLoad { pageCount ->
                if (vm.pendingViewState === viewState) {
                    vm.pendingViewState = null
                }
                hideProgressBar(loadToken, documentUri)
                pdfThemeController.configureTheme()
                createPdfRecord(savePassword, pdf, fileHash, loadToken, documentUri)
                onDocumentLoaded(pageCount, cachedCropMargins, fileHash, loadToken, documentUri, applyDocumentLoadDefaults)
            }
            .load()

        // Show the page scroll handler for a while when the pdf is loaded then hide it.
        pdfView.performTap()
    }

    private fun createPdfRecord(
        savePassword: Boolean,
        pdf: DocumentState,
        expectedFileHash: String?,
        loadToken: Long,
        documentUri: Uri?,
    ) {
        val password = if (savePassword) pdf.password else null
        val documentTitle = runCatching { binding.pdfView.documentMeta?.title }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        scope.launch {
            if (!vm.isCurrent(loadToken, documentUri)) {
                return@launch
            }

            // cannot use elvis operator ?: with a suspend function, it won't wait
            if (pdf.fileHash == null && expectedFileHash == null) {
                val computedHash = computeHash(activity, pdf)
                if (!vm.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                pdf.fileHash = computedHash
            }
            if (!vm.isCurrent(loadToken, documentUri)) {
                return@launch
            }

            val fileHash = expectedFileHash ?: pdf.fileHash
            if (fileHash == null) {
                Log.e(TAG, "createPdfRecord: Failed to compute fileHash while creating PdfRecord")
                return@launch
            }
            pdf.fileHash = fileHash

            if (databaseManager.hasRecord(fileHash)) {
                if (!vm.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                databaseManager.setLastOpened(fileHash, LocalDateTime.now())
                if (!vm.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                updateRecordUri(fileHash, pdf)
                if (!vm.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                if (documentTitle != null) {
                    databaseManager.setDocumentTitle(fileHash, documentTitle)
                }
                if (!vm.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                if (password != null) {
                    databaseManager.setPassword(fileHash, password)
                }
                if (!vm.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                pdf.autoScrollSpeed?.let { databaseManager.setAutoScrollSpeed(fileHash, it) }
                if (!vm.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                readingDirectionResolver.saveState(fileHash)
                if (!vm.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                cropMarginsController.onRecordAvailable(fileHash)
            }
            else {
                if (!vm.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                val record = PdfRecord.from(fileHash, this@DocumentLoadController.pdf, password)
                databaseManager.saveRecordInBackground(record)
                if (!vm.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                if (documentTitle != null) {
                    databaseManager.setDocumentTitle(fileHash, documentTitle)
                }
                if (!vm.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                updateRecordUri(fileHash, pdf)
                if (!vm.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                pdf.autoScrollSpeed?.let { databaseManager.setAutoScrollSpeed(fileHash, it) }
                if (!vm.isCurrent(loadToken, documentUri)) {
                    return@launch
                }
                cropMarginsController.onRecordAvailable(fileHash)
            }
        }
    }

    private suspend fun updateRecordUri(fileHash: String, pdf: DocumentState) {
        val currentUri = pdf.uri ?: return
        val canonicalFile = UriCanonicalizer.canonicalize(activity, currentUri)
        val durableUri = canonicalFile?.let(Uri::fromFile) ?: currentUri
        val storedUri = databaseManager.findRecord(fileHash)?.uri
        if (storedUri?.toString() == durableUri.toString()) {
            return
        }
        databaseManager.updateRecordIdentity(
            fileHash,
            durableUri,
            pdf.name.removeSuffix(".pdf"),
            LocalDateTime.now(),
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createScrollHandle(): ScrollHandle {
        // hiding the handle if the pdf.length is 1 will happen when pdf.length is set in setPdfLength()
        val handle = DefaultScrollHandle(activity, false, pref.getShowScrollHandlePageCount())
        val fullScreenTouchListener = fullScreenOptionsManager.getOnTouchListener()
        handle.setOnTouchListener { view, motionEvent ->
            autoScrollManager.handleUserInteraction(motionEvent)
            fullScreenTouchListener.onTouch(view, motionEvent)
        }
        handle.setOnClickListener { goToPage() }
        return handle
    }

    private fun setCurrentPage(
        pageNumber: Int,
        pageCount: Int,
        expectedFileHash: String?,
        loadToken: Long,
        documentUri: Uri?,
    ) {
        if (!vm.isCurrent(loadToken, documentUri)) {
            return
        }
        vm.setPage(pageNumber)
        setPdfLength(pageCount)
        updateAppTitle()
        readerNavigationController.onPageChanged(pageNumber)
        binding.pdfView.announceForAccessibility(activity.getString(R.string.page_x_of_y, pageNumber + 1, pageCount))

        scope.launch {
            if (!vm.isCurrent(loadToken, documentUri)) {
                return@launch
            }
            // cannot use elvis operator ?: with a suspend function, it won't wait
            val hash = pdf.fileHash ?: expectedFileHash ?: computeHash(activity, pdf)
            if (!vm.isCurrent(loadToken, documentUri)) {
                return@launch
            }
            if (hash != null) {  // Ensure hash is not null
                pdf.fileHash = hash
                readerNavigationController.onFileHashComputed()
                databaseManager.setPageNumber(hash, pageNumber)  // Set the page number in the database
            }
            else {
                showFailedToComputeHashError()
            }
        }
    }

    private fun setPdfLength(pageCount: Int) {
        pdf.initPdfLength(pageCount)
        if (pageCount == 1) {
            fullScreenOptionsManager.permanentlyHidePageHandle()
        }
    }

    fun showFailedToComputeHashError() {
        val message = "Can't hash the file! Last visited page won't be remembered in this session."
        AppSnackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        Log.e(TAG, "showFailedToComputeHashError: $message", RuntimeException())
    }

    private fun reportLoadPageError(page: Int, error: Throwable) {
        val message = activity.resources.getString(R.string.cannot_load_page) + page + " " + error
        AppSnackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        Log.e(TAG, message)
    }

    private fun hideProgressBar(loadToken: Long, documentUri: Uri?) {
        if (vm.isCurrent(loadToken, documentUri)) {
            hideProgressBarNow()
        }
    }

    private companion object {
        const val TAG = "DocumentLoadController"
        const val TILE_CACHE_PIXEL_BUDGET = 2 * 120 * 256 * 256
        const val MIN_TILE_CACHE_SIZE = 24
        const val MAX_TILE_CACHE_SIZE = 480
    }
}
