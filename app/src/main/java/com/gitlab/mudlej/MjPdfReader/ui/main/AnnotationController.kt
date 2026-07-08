package com.gitlab.mudlej.MjPdfReader.ui.main

import android.content.Context
import android.net.Uri
import android.util.Log
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationJournal
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                pdfView.setHighlightAnnotationColor(highlightReference(edit.page, edit.group), edit.color)
            is AnnotationEdit.Delete ->
                pdfView.removeHighlightAnnotation(highlightReference(edit.page, edit.group))
            is AnnotationEdit.SetFieldText ->
                pdfView.setFormFieldText(edit.page, edit.fieldIndex, edit.text)
            is AnnotationEdit.SetFieldChecked ->
                pdfView.setFormFieldChecked(edit.page, edit.fieldIndex, edit.checked)
            is AnnotationEdit.AddStamp ->
                pdfView.addStampAnnotation(edit.page, edit.rect, edit.strokes.toTypedArray(), edit.color, edit.strokeWidth)
        }
        if (!applied) {
            Log.w(TAG, "applyEdit: skipped ${edit.javaClass.simpleName} on page ${edit.page}")
        }
    }

    private fun highlightReference(page: Int, group: String): PDFView.HighlightAnnotation {
        return PDFView.HighlightAnnotation(page, -1, group, null, "")
    }

    companion object {
        private const val TAG = "AnnotationController"

        fun sourceKey(uri: Uri): String = SourceKey.of(uri)
    }
}
