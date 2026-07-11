package com.gitlab.mudlej.MjPdfReader.ui.reader.annotation

import android.content.Context
import android.net.Uri
import android.util.Log
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationJournal
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnnotationController(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val vm: ReaderViewModel,
) {
    private val journal = AnnotationJournal(context)
    private val pdf get() = vm.doc

    val currentSaveDestinationUri: Uri?
        get() = vm.annotationSaveDestinationUri
    val currentSaveDestinationDurable: Boolean
        get() = vm.annotationSaveDestinationDurable
    val hasUnsavedAnnotations: Boolean
        get() = vm.hasUnsavedAnnotations
    val isSaving: Boolean
        get() = vm.isSavingAnnotations

    fun setCurrentSaveDestination(uri: Uri?, durable: Boolean = true) {
        vm.annotationSaveDestinationUri = uri
        vm.annotationSaveDestinationDurable = uri != null && durable
    }

    fun markDirty() {
        vm.hasUnsavedAnnotations = true
    }

    fun clearDirty() {
        vm.hasUnsavedAnnotations = false
    }

    fun setSaving(saving: Boolean) {
        vm.isSavingAnnotations = saving
    }

    fun recordEdit(edit: AnnotationEdit) {
        val uri = pdf.uri ?: return
        journal.append(uri, edit)
        vm.sessionOwnedAnnotationKeys.add(SourceKey.of(uri))
        vm.hasUnsavedAnnotations = true
    }

    fun hasJournal(uri: Uri?): Boolean {
        return uri != null && journal.hasRecords(uri)
    }

    fun isSessionOwned(uri: Uri?): Boolean {
        return uri != null && SourceKey.of(uri) in vm.sessionOwnedAnnotationKeys
    }

    fun markSessionOwned(uri: Uri?) {
        uri?.let { vm.sessionOwnedAnnotationKeys.add(SourceKey.of(it)) }
    }

    fun clearJournal(uri: Uri? = pdf.uri) {
        uri?.let(journal::delete)
        vm.hasUnsavedAnnotations = false
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
        vm.hasUnsavedAnnotations = true
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
            is AnnotationEdit.AddSignature ->
                pdfView.addSignature(edit.page, edit.rect, edit.strokes.toTypedArray(), edit.color, edit.strokeWidth)
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
