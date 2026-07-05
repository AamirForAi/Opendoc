package com.gitlab.mudlej.MjPdfReader.ui.main

import android.content.Context
import android.net.Uri
import android.system.Os
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.PdfBytesHolder
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class AnnotationController(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val pdf: PDF,
) {
    private val saveMutex = Mutex()

    var workingCopyFile: File? = null
        private set
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
        workingCopyFile = uri?.let { workingCopyFileFor(it) }
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

    fun hasWorkingCopy(uri: Uri?): Boolean {
        return uri?.let { workingCopyFileFor(it).isFile } == true
    }

    fun applyWorkingCopyIfPresent(uri: Uri?): Boolean {
        val sourceUri = uri ?: return false
        val file = workingCopyFileFor(sourceUri)
        if (!file.isFile) {
            return false
        }
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return false
        PdfBytesHolder.set(sourceUri.toString(), bytes)
        workingCopyFile = file
        return true
    }

    suspend fun saveWorkingCopy(): Boolean = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            isSaving = true
            try {
                val sourceUri = pdf.uri ?: return@withLock false
                val target = workingCopyFileFor(sourceUri)
                val tmp = File(target.parentFile, "${target.name}.tmp")
                if (tmp.exists() && !tmp.delete()) {
                    return@withLock false
                }

                val saved = runCatching { binding.pdfView.saveAsCopy(tmp) }.getOrDefault(false)
                if (!saved) {
                    tmp.delete()
                    return@withLock false
                }
                if (runCatching { Os.rename(tmp.absolutePath, target.absolutePath) }.isFailure) {
                    tmp.delete()
                    return@withLock false
                }

                val bytes = runCatching { target.readBytes() }.getOrNull() ?: return@withLock false
                workingCopyFile = target
                PdfBytesHolder.set(sourceUri.toString(), bytes)
                true
            } finally {
                isSaving = false
            }
        }
    }

    fun deleteWorkingCopy(uri: Uri? = pdf.uri) {
        uri?.let { workingCopyFileFor(it).delete() }
        workingCopyFile = null
        hasUnsavedAnnotations = false
    }

    fun workingCopyFileFor(uri: Uri): File {
        val dir = File(context.filesDir, "annotations")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "${sourceKey(uri)}.pdf")
    }

    companion object {
        fun sourceKey(uri: Uri): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(uri.toString().toByteArray(Charsets.UTF_8))
            return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}
