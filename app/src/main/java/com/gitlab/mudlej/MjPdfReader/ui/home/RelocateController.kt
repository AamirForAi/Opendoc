package com.gitlab.mudlej.MjPdfReader.ui.home

import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManager
import com.gitlab.mudlej.MjPdfReader.manager.storage.LibraryScanner
import com.gitlab.mudlej.MjPdfReader.repository.PdfRecord
import com.gitlab.mudlej.MjPdfReader.util.PersistedGrantKeeper
import com.gitlab.mudlej.MjPdfReader.util.UriCanonicalizer
import com.gitlab.mudlej.MjPdfReader.util.computeHash
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class RelocateController(
    private val activity: AppCompatActivity,
    private val databaseManager: DatabaseManager,
    private val libraryScanner: LibraryScanner,
    private val scope: CoroutineScope,
    private val onOpen: (Uri, String?) -> Unit,
    private val onHealed: () -> Unit,
) {

    private var pendingRecord: PdfRecord? = null

    private val relocatePicker =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val record = pendingRecord
            pendingRecord = null
            if (uri != null && record != null) {
                onFilePicked(record, uri)
            }
        }

    fun handleMissingFile(hash: String) {
        scope.launch {
            val record = databaseManager.findRecord(hash) ?: return@launch

            val healedPath = libraryScanner.findPathByHash(hash)
            if (healedPath != null) {
                val file = File(healedPath)
                databaseManager.updateRecordIdentity(
                    hash, Uri.fromFile(file), file.nameWithoutExtension, record.lastOpened
                )
                Toast.makeText(activity, R.string.home_relocate_found, Toast.LENGTH_SHORT).show()
                onHealed()
                return@launch
            }

            showRelocateDialog(record)
        }
    }

    private fun showRelocateDialog(record: PdfRecord) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.home_relocate_title)
            .setMessage(activity.getString(R.string.home_relocate_message, record.fileName))
            .setPositiveButton(R.string.home_relocate_action) { _, _ ->
                pendingRecord = record
                relocatePicker.launch(arrayOf(PDF.FILE_TYPE))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun onFilePicked(record: PdfRecord, uri: Uri) {
        scope.launch {
            val pickedHash = computeHash(activity, PDF(uri = uri))
            if (pickedHash == record.hash) {
                PersistedGrantKeeper.takeReadGrant(activity, uri)
                val canonicalFile = UriCanonicalizer.canonicalize(activity, uri)
                val durableUri = canonicalFile?.let(Uri::fromFile) ?: uri
                databaseManager.updateRecordIdentity(
                    record.hash, durableUri, record.fileName, record.lastOpened
                )
                onHealed()
                onOpen(durableUri, record.hash)
            } else {
                showMismatchDialog(uri)
            }
        }
    }

    private fun showMismatchDialog(uri: Uri) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.home_relocate_mismatch_title)
            .setMessage(R.string.home_relocate_mismatch_message)
            .setPositiveButton(R.string.home_open_anyway) { _, _ ->
                PersistedGrantKeeper.takeReadGrant(activity, uri)
                onOpen(uri, null)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
