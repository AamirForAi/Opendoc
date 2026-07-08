package com.gitlab.mudlej.MjPdfReader.ui.main

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher
import androidx.core.net.toUri
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.PdfBytesHolder
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManager
import com.gitlab.mudlej.MjPdfReader.repository.PdfAnnotationSaveDestination
import com.gitlab.mudlej.MjPdfReader.repository.PdfRecord
import com.gitlab.mudlej.MjPdfReader.util.computeHash
import com.gitlab.mudlej.MjPdfReader.util.getFileName
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

class AnnotationSaveController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val pdf: PDF,
    private val annotationController: AnnotationController,
    private val databaseManager: DatabaseManager,
    private val scope: CoroutineScope,
    private val updateDestinationLauncher: ActivityResultLauncher<Intent>,
    private val createDestinationLauncher: ActivityResultLauncher<Intent>,
    private val clearActiveSearchResultHighlight: () -> Unit,
    private val updateDirtyUi: () -> Unit,
    private val beforeSave: () -> Boolean,
    private val onDocumentSaved: () -> Unit,
) {
    private data class SaveBytes(val bytes: ByteArray, val hash: String)

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
        if (!annotationController.acceptsDocumentUri(sourceUri)) {
            pendingPostSaveAction = null
            return
        }
        val grantPersisted = persistWritePermission(destinationUri, intent)
        saveHighlightsToUri(destinationUri, sourceUri, saveDestinationDurably = grantPersisted)
    }

    suspend fun resolveCurrentDestination(documentUri: Uri?) {
        if (documentUri == null) {
            annotationController.setCurrentSaveDestination(null)
            return
        }
        val destination = databaseManager.findAnnotationSaveDestinationByDestinationUri(documentUri.toString())
        annotationController.setCurrentSaveDestination(destination?.destinationUri?.toUri())
    }

    private fun showSaveDestinationSheet() {
        val sourceUri = pdf.uri ?: return
        scope.launch {
            val savedCopyDestination = findDurableSavedCopyDestination(sourceUri, pdf.fileHash)
            showSaveDestinationSheet(savedCopyDestination)
        }
    }

    private suspend fun findDurableSavedCopyDestination(sourceUri: Uri, fileHash: String?): Uri? {
        val sourceKeyDestination = databaseManager.findAnnotationSaveDestinationBySourceKey(AnnotationController.sourceKey(sourceUri))
        val hashDestination = fileHash?.let { databaseManager.findAnnotationSaveDestinationByLastSavedHash(it) }
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
                    if (savedCopyDestination != null) {
                        saveHighlightsToUri(savedCopyDestination, sourceUri, saveDestinationDurably = true)
                    } else {
                        launchUpdateDestinationPicker()
                    }
                } else {
                    launchCreateDestinationPicker()
                }
            }
            .setOnCancelListener { clearPendingRequests() }
            .show()
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
            pdf.uri = destinationUri
            pdf.name = destinationName
            pdf.fileHash = newHash
            PdfBytesHolder.set(destinationUri.toString(), saveResult.bytes)

            if (oldHash != null) {
                databaseManager.copyOrUpdateRecordIdentity(
                    oldHash,
                    newHash,
                    sourceUri,
                    destinationUri,
                    destinationName.removeSuffix(".pdf"),
                )
            } else {
                databaseManager.saveRecordInBackground(PdfRecord.from(newHash, pdf, pdf.password))
            }
            if (saveDestinationDurably) {
                databaseManager.saveAnnotationSaveDestination(
                    PdfAnnotationSaveDestination(
                        sourceKey = AnnotationController.sourceKey(sourceUri),
                        destinationUri = destinationUri.toString(),
                        lastSavedHash = newHash,
                        lastSavedAt = LocalDateTime.now(),
                    )
                )
            }

            annotationController.clearJournal(sourceUri)
            annotationController.setCurrentSaveDestination(destinationUri, durable = saveDestinationDurably)
            annotationController.setSaving(false)
            updateDirtyUi()
            onDocumentSaved()
            activity.setTaskDescription(ActivityManager.TaskDescription(pdf.name))
            Snackbar.make(binding.root, R.string.highlights_saved, Snackbar.LENGTH_SHORT).show()
            val postSaveAction = pendingPostSaveAction
            pendingPostSaveAction = null
            postSaveAction?.invoke()
        }
    }

    private suspend fun writeDocumentToSaf(destinationUri: Uri): SaveBytes? = withContext(Dispatchers.IO) {
        val tmp = File(activity.cacheDir, "annotation-save-${System.currentTimeMillis()}.pdf")
        try {
            if (!runCatching { binding.pdfView.saveAsCopy(tmp) }.getOrDefault(false)) {
                return@withContext null
            }
            val bytes = runCatching { tmp.readBytes() }.getOrNull() ?: return@withContext null
            val hash = computeHash(bytes) ?: return@withContext null
            val wrote = runCatching {
                activity.contentResolver.openOutputStream(destinationUri, "wt")?.use { output ->
                    tmp.inputStream().use { input -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (!wrote) {
                return@withContext null
            }
            SaveBytes(bytes, hash)
        } finally {
            tmp.delete()
        }
    }

    private fun showSaveFailed() {
        Snackbar.make(binding.root, R.string.highlight_save_failed, Snackbar.LENGTH_LONG)
            .setAction(R.string.choose) { showSaveDestinationSheet() }
            .show()
    }
}
