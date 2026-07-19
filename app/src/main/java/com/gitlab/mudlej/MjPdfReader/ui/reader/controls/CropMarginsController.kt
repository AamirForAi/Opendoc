// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.controls

import android.app.Activity
import android.net.Uri
import android.os.SystemClock
import android.view.View
import com.github.barteksc.pdfviewer.CropMarginDetection
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.PDFView.Configurator
import com.github.barteksc.pdfviewer.model.CropMargins
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.ui.reader.DocumentState
import android.os.ParcelFileDescriptor
import com.gitlab.mudlej.MjPdfReader.data.OnlineDocumentStore
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.data.HistoryPolicy
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CropMarginsController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val pdfRepository: PdfRepository,
    private val historyPolicy: HistoryPolicy,
    private val pdf: DocumentState,
    private val scope: CoroutineScope,
    private val isCropMarginsEnabled: () -> Boolean,
    private val setCropMarginsEnabled: (Boolean) -> Unit,
    private val isCurrentDocument: (Long, Uri?) -> Boolean,
    private val reloadWithCropMargins: (Configurator, Int, CropMargins, PDFView.ViewState?) -> Unit,
) {

    @Volatile
    private var pendingCropMargins: PendingCropMargins? = null
    private var detectionJob: Job? = null
    private var detectionCancel: AtomicBoolean? = null
    private var lastStatusTextUpdate = 0L

    init {
        binding.cropDetectionCancelButton.setOnClickListener { cancelByUser() }
    }

    suspend fun findCached(fileHash: String?): CropMargins? {
        if (!isCropMarginsEnabled() || fileHash == null) {
            return null
        }
        val storedCropMargins = pdfRepository.findCropMargins(fileHash, CropMargins.STORAGE_VERSION)
            ?: return null
        return CropMargins.fromStorageString(storedCropMargins)
    }

    fun startIfNeeded(
        cachedCropMargins: CropMargins?,
        fileHash: String?,
        loadToken: Long,
        documentUri: Uri?,
        pageCount: Int,
    ) {
        if (!isCropMarginsEnabled()
            || cachedCropMargins != null
            || fileHash == null
            || documentUri == null
            || pageCount <= 0
        ) {
            hideStatus()
            return
        }

        cancel()
        val cancelFlag = AtomicBoolean(false)
        detectionCancel = cancelFlag
        lastStatusTextUpdate = 0L

        detectionJob = scope.launch(Dispatchers.Default) {
            val result = detect(documentUri, cancelFlag, loadToken)
            if (result == null || cancelFlag.get()) {
                withContext(Dispatchers.Main) {
                    hideStatus()
                    detectionJob = null
                    detectionCancel = null
                }
                return@launch
            }
            if (!isCurrentDocument(loadToken, documentUri)) {
                return@launch
            }

            save(fileHash, result)

            withContext(Dispatchers.Main) {
                if (!isCurrentDocument(loadToken, documentUri) || cancelFlag.get()) {
                    return@withContext
                }
                hideStatus()
                detectionJob = null
                detectionCancel = null
                if (!result.isFullPage && isCropMarginsEnabled()) {
                    apply(result)
                }
            }
        }
    }

    fun startOrApply(fileHash: String?, loadToken: Long, documentUri: Uri?, pageCount: Int) {
        if (!isCropMarginsEnabled() || fileHash == null || documentUri == null || pageCount <= 0) {
            return
        }

        scope.launch {
            val cachedCropMargins = findCached(fileHash)
            withContext(Dispatchers.Main) {
                if (!isCurrentDocument(loadToken, documentUri) || !isCropMarginsEnabled()) {
                    return@withContext
                }
                if (cachedCropMargins == null) {
                    startIfNeeded(null, fileHash, loadToken, documentUri, pageCount)
                } else if (!cachedCropMargins.isFullPage) {
                    apply(cachedCropMargins)
                }
            }
        }
    }

    fun cancel() {
        detectionCancel?.set(true)
        detectionJob?.cancel()
        detectionJob = null
        detectionCancel = null
        hideStatus()
    }

    private fun cancelByUser() {
        cancel()
        if (isCropMarginsEnabled()) {
            setCropMarginsEnabled(false)
        }
    }

    suspend fun onRecordAvailable(fileHash: String) {
        if (!historyPolicy.canRecord()) {
            return
        }
        val pending = pendingCropMargins ?: return
        if (pending.fileHash != fileHash) {
            return
        }
        pdfRepository.setCropMargins(
            fileHash,
            pending.cropMargins.toStorageString(),
            pending.cropMargins.version,
        )
        if (pendingCropMargins == pending) {
            pendingCropMargins = null
        }
    }

    private suspend fun detect(documentUri: Uri, cancelFlag: AtomicBoolean, loadToken: Long): CropMargins? {
        return try {
            val openedDocument = openDocument(documentUri) ?: return null
            try {
                val total = openedDocument.core.getPageCount(openedDocument.document)
                CropMarginDetection.detect(
                    openedDocument.core,
                    openedDocument.document,
                    total,
                    { done, pageCount -> updateStatus(loadToken, documentUri, cancelFlag, done, pageCount) },
                    { cancelFlag.get() || !isCurrentDocument(loadToken, documentUri) },
                )
            } finally {
                openedDocument.core.closeDocument(openedDocument.document)
            }
        } catch (throwable: Throwable) {
            null
        }
    }

    private fun openDocument(documentUri: Uri): OpenedDocument? {
        val pdfiumCore = PdfiumCore(activity)
        val password = pdf.password
        val heldFile = OnlineDocumentStore.fileFor(activity, documentUri.toString())
        val fileDescriptor = if (heldFile != null) {
            ParcelFileDescriptor.open(heldFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            activity.contentResolver.openFileDescriptor(documentUri, "r") ?: return null
        }
        val document: PdfDocument = try {
            pdfiumCore.newDocument(fileDescriptor, password)
        } catch (throwable: Throwable) {
            runCatching { fileDescriptor.close() }
            throw throwable
        }
        return OpenedDocument(pdfiumCore, document)
    }

    private fun apply(cropMargins: CropMargins) {
        val uri = pdf.uri ?: return
        val configurator = currentConfigurator(uri) ?: return
        val viewState = binding.pdfView.captureViewState()
        val currentPage = viewState?.pageIndex ?: binding.pdfView.currentPage
        reloadWithCropMargins(configurator, currentPage, cropMargins, viewState)
    }

    private fun currentConfigurator(uri: Uri): Configurator? {
        val file = OnlineDocumentStore.fileFor(activity, uri.toString())
        return if (file != null) {
            binding.pdfView.fromFile(file)
        } else {
            binding.pdfView.fromUri(uri)
        }
    }

    private suspend fun save(fileHash: String, cropMargins: CropMargins) {
        if (!historyPolicy.canRecord()) {
            return
        }
        if (pdfRepository.hasRecord(fileHash)) {
            pdfRepository.setCropMargins(
                fileHash,
                cropMargins.toStorageString(),
                cropMargins.version,
            )
        } else {
            val pending = PendingCropMargins(fileHash, cropMargins)
            pendingCropMargins = pending
            if (pdfRepository.hasRecord(fileHash) && pendingCropMargins == pending) {
                pdfRepository.setCropMargins(
                    fileHash,
                    cropMargins.toStorageString(),
                    cropMargins.version,
                )
                if (pendingCropMargins == pending) {
                    pendingCropMargins = null
                }
            }
        }
    }

    private fun updateStatus(loadToken: Long, documentUri: Uri, cancelFlag: AtomicBoolean, done: Int, total: Int) {
        if (total <= 0 || cancelFlag.get()) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (done < total && now - lastStatusTextUpdate < 150L) {
            return
        }
        lastStatusTextUpdate = now

        activity.runOnUiThread {
            if (!isCurrentDocument(loadToken, documentUri) || cancelFlag.get()) {
                return@runOnUiThread
            }
            val progress = done.coerceIn(0, total)
            binding.cropDetectionProgress.max = total
            binding.cropDetectionProgress.progress = progress
            binding.cropDetectionStatusText.text = activity.getString(R.string.analyzing_margins_progress, progress, total)
            binding.cropDetectionStatusCard.visibility = View.VISIBLE
        }
    }

    private fun hideStatus() {
        binding.cropDetectionStatusCard.visibility = View.GONE
    }

    private data class PendingCropMargins(val fileHash: String, val cropMargins: CropMargins)

    private data class OpenedDocument(val core: PdfiumCore, val document: PdfDocument)
}
