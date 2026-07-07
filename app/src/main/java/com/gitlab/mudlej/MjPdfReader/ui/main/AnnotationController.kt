package com.gitlab.mudlej.MjPdfReader.ui.main

import android.content.Context
import android.net.Uri
import android.util.Log
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.PdfBytesHolder
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationJournal
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AnnotationController(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val pdf: PDF,
) {
    private val journal = AnnotationJournal(context)
    private val sessionOwnedSourceKeys = mutableSetOf<String>()

    var currentSaveDestinationUri: Uri? = null
        private set
    var currentSaveDestinationDurable: Boolean = false
        private set
    private var loadedDocumentUri: Uri? = null
    var hasUnsavedAnnotations: Boolean = false
        private set
    var isSaving: Boolean = false
        private set

    fun resetForDocument(uri: Uri?) {
        loadedDocumentUri = uri
        currentSaveDestinationUri = null
        currentSaveDestinationDurable = false
        hasUnsavedAnnotations = false
        isSaving = false
    }

    fun acceptsDocumentUri(uri: Uri?): Boolean {
        return uri == null || uri == pdf.uri || uri == loadedDocumentUri
    }

    fun setCurrentSaveDestination(uri: Uri?, durable: Boolean = true) {
        currentSaveDestinationUri = uri
        currentSaveDestinationDurable = uri != null && durable
    }

    fun markDirty() {
        hasUnsavedAnnotations = true
    }

    fun clearDirty() {
        hasUnsavedAnnotations = false
    }

    fun setSaving(saving: Boolean) {
        isSaving = saving
    }

    fun recordEdit(edit: AnnotationEdit) {
        val uri = pdf.uri ?: return
        journal.append(uri, edit)
        sessionOwnedSourceKeys.add(SourceKey.of(uri))
        hasUnsavedAnnotations = true
    }

    fun hasJournal(uri: Uri?): Boolean {
        return uri != null && journal.hasRecords(uri)
    }

    fun isSessionOwned(uri: Uri?): Boolean {
        return uri != null && SourceKey.of(uri) in sessionOwnedSourceKeys
    }

    fun markSessionOwned(uri: Uri?) {
        uri?.let { sessionOwnedSourceKeys.add(SourceKey.of(it)) }
    }

    fun sessionOwnedKeysForState(): ArrayList<String> {
        return ArrayList(sessionOwnedSourceKeys)
    }

    fun restoreSessionOwnedKeys(keys: List<String>?) {
        keys?.let(sessionOwnedSourceKeys::addAll)
    }

    fun clearJournal(uri: Uri? = pdf.uri) {
        uri?.let(journal::delete)
        hasUnsavedAnnotations = false
    }

    suspend fun replayJournal(): Boolean {
        val uri = pdf.uri ?: return false
        val edits = withContext(Dispatchers.IO) { journal.readAll(uri) }
        if (edits.isEmpty()) {
            return false
        }
        withContext(Dispatchers.Main) {
            edits.forEach(::applyEdit)
        }
        markSessionOwned(uri)
        hasUnsavedAnnotations = true
        return true
    }

    private fun applyEdit(edit: AnnotationEdit) {
        val pdfView = binding.pdfView
        val applied = when (edit) {
            is AnnotationEdit.Add ->
                pdfView.addHighlightAnnotation(edit.page, edit.rects, edit.color, edit.contents, edit.group)
            is AnnotationEdit.Recolor ->
                pdfView.setHighlightAnnotationColor(highlightReference(edit), edit.color)
            is AnnotationEdit.Delete ->
                pdfView.removeHighlightAnnotation(highlightReference(edit))
        }
        if (!applied) {
            Log.w(TAG, "applyEdit: skipped ${edit.javaClass.simpleName} on page ${edit.page}")
        }
    }

    private fun highlightReference(edit: AnnotationEdit): PDFView.HighlightAnnotation {
        return PDFView.HighlightAnnotation(edit.page, -1, edit.group, null, "")
    }

    // TODO(remove after one release): legacy pre-journal working-copy recovery.
    fun hasWorkingCopy(uri: Uri?): Boolean {
        return uri?.let { workingCopyFileFor(it).isFile } == true
    }

    // TODO(remove after one release): legacy pre-journal working-copy recovery.
    fun applyWorkingCopyIfPresent(uri: Uri?): Boolean {
        val sourceUri = uri ?: return false
        val file = workingCopyFileFor(sourceUri)
        if (!file.isFile) {
            return false
        }
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return false
        PdfBytesHolder.set(sourceUri.toString(), bytes)
        return true
    }

    // TODO(remove after one release): legacy pre-journal working-copy recovery.
    fun deleteWorkingCopy(uri: Uri? = pdf.uri) {
        uri?.let { workingCopyFileFor(it).delete() }
        hasUnsavedAnnotations = false
    }

    private fun workingCopyFileFor(uri: Uri): File {
        val dir = File(context.filesDir, "annotations")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "${sourceKey(uri)}.pdf")
    }

    companion object {
        private const val TAG = "AnnotationController"

        fun sourceKey(uri: Uri): String = SourceKey.of(uri)
    }
}
