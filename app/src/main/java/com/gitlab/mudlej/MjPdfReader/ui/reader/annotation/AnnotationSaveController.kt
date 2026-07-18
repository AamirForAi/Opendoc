// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.annotation

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher
import androidx.core.net.toUri
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.ui.reader.DocumentState
import com.gitlab.mudlej.MjPdfReader.ui.reader.load.PreviewDiskCoordinator
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.data.HistoryPolicy
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfAnnotationSaveDestination
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import com.gitlab.mudlej.MjPdfReader.core.ui.AppSnackbar
import com.gitlab.mudlej.MjPdfReader.core.PermissionManager
import com.gitlab.mudlej.MjPdfReader.core.io.UriCanonicalizer
import com.gitlab.mudlej.MjPdfReader.core.io.computeHash
import com.gitlab.mudlej.MjPdfReader.core.io.getFileName
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnnotationSaveController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val pdf: DocumentState,
    private val annotationController: AnnotationController,
    private val pdfRepository: PdfRepository,
    private val historyPolicy: HistoryPolicy,
    private val vm: ReaderViewModel,
    private val scope: CoroutineScope,
    private val updateDestinationLauncher: ActivityResultLauncher<Intent>,
    private val createDestinationLauncher: ActivityResultLauncher<Intent>,
    private val clearActiveSearchResultHighlight: () -> Unit,
    private val updateDirtyUi: () -> Unit,
    private val beforeSave: () -> Boolean,
    private val onDocumentSaved: () -> Unit,
) {
    private data class SaveResult(val hash: String, val sizeBytes: Long)

    private var pendingSourceUri: Uri? = null
    private var pendingPostSaveAction: (() -> Unit)? = null

    fun saveHighlights(postSaveAction: (() -> Unit)? = null) {
        if (!annotationController.hasUnsavedAnnotations || annotationController.isSaving) {
            return
        }
        if (!beforeSave()) {
            return
        }
        pendingPostSaveAction = postSaveAction
        val destinationUri = annotationController.currentSaveDestinationUri
        if (destinationUri != null) {
            saveHighlightsToUri(
                destinationUri,
                pdf.uri ?: return,
                saveDestinationDurably = annotationController.currentSaveDestinationDurable,
            )
        } else {
            showSaveDestinationSheet()
        }
    }

    fun clearPendingRequests() {
        pendingSourceUri = null
        pendingPostSaveAction = null
    }

    fun handleDestinationResult(intent: Intent?) {
        val destinationUri = intent?.data
        if (destinationUri == null) {
            clearPendingRequests()
            return
        }
        val sourceUri = pendingSourceUri ?: pdf.uri
        if (sourceUri == null) {
            clearPendingRequests()
            return
        }
        pendingSourceUri = null
        if (!vm.acceptsDocumentUri(sourceUri)) {
            pendingPostSaveAction = null
            return
        }
        val grantPersisted = persistWritePermission(destinationUri, intent)
        saveHighlightsToUri(destinationUri, sourceUri, saveDestinationDurably = grantPersisted)
    }

    private fun showSaveDestinationSheet() {
        val sourceUri = pdf.uri ?: return
        scope.launch {
            val savedCopyDestination = findDurableSavedCopyDestination(sourceUri, pdf.fileHash)
            showSaveDestinationSheet(savedCopyDestination)
        }
    }

    private suspend fun findDurableSavedCopyDestination(sourceUri: Uri, fileHash: String?): Uri? {
        val sourceKeyDestination = pdfRepository.findAnnotationSaveDestinationBySourceKey(AnnotationController.sourceKey(sourceUri))
        val hashDestination = fileHash?.let { pdfRepository.findAnnotationSaveDestinationByLastSavedHash(it) }
        return listOfNotNull(sourceKeyDestination, hashDestination)
            .map { it.destinationUri.toUri() }
            .firstOrNull { destinationUri ->
                destinationUri.toString() != sourceUri.toString() && hasPersistedWritePermission(destinationUri)
            }
    }

    private fun hasPersistedWritePermission(uri: Uri): Boolean {
        return activity.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isWritePermission
        }
    }

    private fun showSaveDestinationSheet(savedCopyDestination: Uri?) {
        val sourceUri = pdf.uri ?: return
        val options = arrayOf(
            activity.getString(R.string.update_existing_file),
            activity.getString(R.string.save_as_new_copy),
        )
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.save_highlights)
            .setItems(options) { _, which ->
                if (which == 0) {
                    val directDestination = directWriteDestination(sourceUri)
                    when {
                        directDestination != null ->
                            saveHighlightsToUri(directDestination, sourceUri, saveDestinationDurably = true)
                        savedCopyDestination != null ->
                            saveHighlightsToUri(savedCopyDestination, sourceUri, saveDestinationDurably = true)
                        else -> launchUpdateDestinationPicker()
                    }
                } else {
                    launchCreateDestinationPicker()
                }
            }
            .setOnCancelListener { clearPendingRequests() }
            .show()
    }

    private fun directWriteDestination(sourceUri: Uri): Uri? {
        if (!PermissionManager.hasFullAccess(activity)) {
            return null
        }
        val file = UriCanonicalizer.canonicalize(activity, sourceUri) ?: return null
        if (!file.canWrite()) {
            return null
        }
        return Uri.fromFile(file)
    }

    private fun launchUpdateDestinationPicker() {
        val sourceUri = pdf.uri ?: return
        pendingSourceUri = sourceUri
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(PDF.FILE_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, sourceUri)
        updateDestinationLauncher.launch(intent)
    }

    private fun launchCreateDestinationPicker() {
        val sourceUri = pdf.uri ?: return
        pendingSourceUri = sourceUri
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(PDF.FILE_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            .putExtra(Intent.EXTRA_TITLE, suggestedAnnotatedFileName())
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, sourceUri)
        createDestinationLauncher.launch(intent)
    }

    private fun persistWritePermission(uri: Uri, intent: Intent): Boolean {
        val flags = intent.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        if (flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0) {
            return false
        }
        return runCatching {
            activity.contentResolver.takePersistableUriPermission(uri, flags)
            true
        }.getOrDefault(false)
    }

    private fun suggestedAnnotatedFileName(): String {
        val name = pdf.name.ifBlank { "document.pdf" }
        if (name.endsWith("-annotated.pdf", ignoreCase = true)) {
            return name
        }
        return if (name.endsWith(".pdf", ignoreCase = true)) {
            name.dropLast(4) + "-annotated.pdf"
        } else {
            "$name-annotated.pdf"
        }
    }

    private fun saveHighlightsToUri(destinationUri: Uri, sourceUri: Uri, saveDestinationDurably: Boolean = true) {
        clearActiveSearchResultHighlight()
        annotationController.setSaving(true)
        updateDirtyUi()
        val loadToken = vm.currentLoadToken

        scope.launch {
            val oldHash = pdf.fileHash
            val saveResult = writeDocumentToSaf(destinationUri)

            if (saveResult == null) {
                annotationController.setSaving(false)
                pendingPostSaveAction = null
                updateDirtyUi()
                showSaveFailed()
                return@launch
            }

            val destinationName = getFileName(activity, destinationUri)
            val newHash = saveResult.hash
            val isCurrent = vm.isCurrent(loadToken, sourceUri)
            if (isCurrent) {
                pdf.uri = destinationUri
                pdf.name = destinationName
                pdf.fileHash = newHash
            }

            if (historyPolicy.canRecord()) {
                if (oldHash != null) {
                    pdfRepository.copyOrUpdateRecordIdentity(
                        oldHash,
                        newHash,
                        sourceUri,
                        destinationUri,
                        destinationName.removeSuffix(".pdf"),
                    )
                } else if (isCurrent) {
                    pdfRepository.saveRecordInBackground(pdf.toPdfRecord(newHash, pdf.password))
                }
                if (saveDestinationDurably) {
                    pdfRepository.saveAnnotationSaveDestination(
                        PdfAnnotationSaveDestination(
                            sourceKey = AnnotationController.sourceKey(sourceUri),
                            destinationUri = destinationUri.toString(),
                            lastSavedHash = newHash,
                            lastSavedAt = LocalDateTime.now(),
                        )
                    )
                }
            }

            annotationController.clearJournal(sourceUri)
            annotationController.setSaving(false)
            updateDirtyUi()
            if (!isCurrent) {
                pendingPostSaveAction = null
                return@launch
            }
            PreviewDiskCoordinator.attach(
                pdfView = binding.pdfView,
                cacheDir = activity.cacheDir,
                fileHash = newHash,
                pageCount = binding.pdfView.getPageCount(),
                sizeBytes = saveResult.sizeBytes,
                incognito = vm.incognito,
                hasPassword = pdf.password != null,
            )
            annotationController.setCurrentSaveDestination(destinationUri, durable = saveDestinationDurably)
            onDocumentSaved()
            activity.setTaskDescription(ActivityManager.TaskDescription(pdf.name))
            AppSnackbar.make(binding.root, R.string.highlights_saved, Snackbar.LENGTH_SHORT).show()
            val postSaveAction = pendingPostSaveAction
            pendingPostSaveAction = null
            postSaveAction?.invoke()
        }
    }

    private suspend fun writeDocumentToSaf(destinationUri: Uri): SaveResult? = withContext(Dispatchers.IO) {
        val tmp = File(activity.cacheDir, "annotation-save-${System.currentTimeMillis()}.pdf")
        try {
            if (!runCatching { binding.pdfView.saveAsCopy(tmp) }.getOrDefault(false)) {
                return@withContext null
            }
            val sizeBytes = tmp.length()
            val hash = computeHash(tmp) ?: return@withContext null
            val wrote = runCatching {
                activity.contentResolver.openOutputStream(destinationUri, "wt")?.use { output ->
                    tmp.inputStream().use { input -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (!wrote) {
                return@withContext null
            }
            SaveResult(hash, sizeBytes)
        } finally {
            tmp.delete()
        }
    }

    private fun showSaveFailed() {
        AppSnackbar.make(binding.root, R.string.highlight_save_failed, Snackbar.LENGTH_LONG)
            .setAction(R.string.choose) { showSaveDestinationSheet() }
            .show()
    }
}
