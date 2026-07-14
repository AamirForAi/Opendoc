// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.load

import android.content.Context
import android.net.Uri
import android.util.Log
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.io.DurableCopyStore
import com.gitlab.mudlej.MjPdfReader.core.io.PersistedGrantKeeper
import com.gitlab.mudlej.MjPdfReader.core.io.UriCanonicalizer
import com.gitlab.mudlej.MjPdfReader.data.HistoryPolicy
import com.gitlab.mudlej.MjPdfReader.data.PdfBytesHolder
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class TemporaryCopyImporter(
    private val context: Context,
    private val pdfRepository: PdfRepository,
    private val historyPolicy: HistoryPolicy,
    private val pref: Preferences,
    private val currentDocument: () -> Pair<Uri?, String>,
    private val scope: CoroutineScope,
    private val onCopySaved: (String, Int) -> Unit,
) : DocumentListener {

    override fun onRecordAvailable(fileHash: String) {
        if (!pref.getKeepSharedCopies() || !historyPolicy.canRecord()) {
            return
        }
        val (uri, name) = currentDocument()
        if (uri == null || uri.scheme != "content") {
            return
        }
        if (PdfBytesHolder.bytesFor(uri.toString()) != null) {
            return
        }
        val authority = uri.authority ?: return
        if (authority in EXCLUDED_AUTHORITIES || authority.startsWith(BuildConfig.APPLICATION_ID)) {
            return
        }
        synchronized(inFlightHashes) {
            if (!inFlightHashes.add(fileHash)) {
                return
            }
        }
        scope.launch {
            try {
                importIfTemporary(fileHash, uri, name)
            } catch (throwable: Throwable) {
                Log.e(TAG, "importIfTemporary failed for $uri", throwable)
            } finally {
                synchronized(inFlightHashes) { inFlightHashes.remove(fileHash) }
            }
        }
    }

    private suspend fun importIfTemporary(fileHash: String, uri: Uri, name: String) {
        if (PersistedGrantKeeper.takeReadGrant(context, uri)) {
            return
        }
        if (UriCanonicalizer.canonicalize(context, uri) != null) {
            return
        }
        val record = pdfRepository.findRecord(fileHash) ?: return
        val scannedFile = pdfRepository.findScannedPdfsByHash(fileHash)
            .map { File(it.path) }
            .firstOrNull { it.canRead() }
        if (scannedFile != null) {
            pdfRepository.updateRecordIdentity(fileHash, Uri.fromFile(scannedFile), record.fileName, record.lastOpened)
            pdfRepository.setRecordSourceUri(fileHash, uri.toString())
            return
        }
        if (record.uri != uri && isReadable(record.uri)) {
            return
        }
        val copy = DurableCopyStore.saveCopy(context, uri, name, fileHash) ?: return
        pdfRepository.updateRecordIdentity(fileHash, copy.uri, record.fileName, record.lastOpened)
        pdfRepository.setRecordSourceUri(fileHash, uri.toString())
        onCopySaved(fileHash, if (copy.isPublic) R.string.shared_copy_saved_public else R.string.shared_copy_saved_private)
    }

    private fun isReadable(uri: Uri): Boolean {
        return if (uri.scheme == "file") {
            uri.path?.let { File(it).canRead() } == true
        } else {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { } != null
            }.getOrDefault(false)
        }
    }

    private companion object {
        const val TAG = "TemporaryCopyImporter"
        val inFlightHashes: MutableSet<String> = mutableSetOf()
        val EXCLUDED_AUTHORITIES = setOf(
            "media",
            "com.android.externalstorage.documents",
            "com.android.providers.downloads.documents",
            "com.android.providers.media.documents",
        )
    }
}
