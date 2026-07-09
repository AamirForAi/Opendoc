package com.gitlab.mudlej.MjPdfReader.ui.home

import android.net.Uri
import android.provider.DocumentsContract
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.DialogRecordOptionsBinding
import com.gitlab.mudlej.MjPdfReader.databinding.DialogRenameRecordBinding
import com.gitlab.mudlej.MjPdfReader.enums.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManager
import com.gitlab.mudlej.MjPdfReader.manager.storage.LibraryScanner
import com.gitlab.mudlej.MjPdfReader.manager.thumbnail.CoverCache
import com.gitlab.mudlej.MjPdfReader.repository.PdfRecord
import com.gitlab.mudlej.MjPdfReader.ui.showMetaDialog
import com.gitlab.mudlej.MjPdfReader.util.appDateFormatter
import com.gitlab.mudlej.MjPdfReader.util.computeHash
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.io.File
import java.time.LocalDateTime
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordOptionsDialog(
    private val activity: AppCompatActivity,
    private val databaseManager: DatabaseManager,
    private val coverCache: CoverCache,
    private val libraryScanner: LibraryScanner,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
) {

    fun show(item: HomeItem) {
        scope.launch {
            val record = databaseManager.findRecord(item.hash)
            buildAndShow(item, record)
        }
    }

    private fun buildAndShow(item: HomeItem, record: PdfRecord?) {
        val binding = DialogRecordOptionsBinding.inflate(activity.layoutInflater)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .create()

        binding.optionsTitle.text = item.title
        binding.optionsInfo.text = buildInfoLine(item, item.length)
        coverCache.bind(binding.optionsCover, item.coverKey, item.uri, COVER_WIDTH_PX, scope)

        if (item.length <= 0) {
            scope.launch {
                val meta = withContext(Dispatchers.IO) { readDocumentMeta(item.uri) }
                val pages = meta?.totalPages ?: 0
                if (pages > 0) {
                    binding.optionsInfo.text = buildInfoLine(item, pages)
                }
            }
        }

        if (item.progressPercent > 0) {
            binding.optionsProgress.visibility = View.VISIBLE
            binding.optionsProgress.progress = item.progressPercent
            binding.optionsPercent.visibility = View.VISIBLE
            binding.optionsPercent.text = activity.getString(
                R.string.home_percent_position_template,
                item.progressPercent,
                item.pageNumber + 1,
                item.length,
            )
        }

        if (item.hasBeenOpened) {
            binding.optionsLastRead.visibility = View.VISIBLE
            binding.optionsLastRead.text = activity.getString(
                R.string.home_last_read, item.lastOpened.format(appDateFormatter)
            )
        }

        bindStatus(binding, item, record)

        if (item.hasBeenOpened && record != null) {
            binding.removeRecentButton.visibility = View.VISIBLE
            binding.removeRecentButton.setOnClickListener {
                dialog.dismiss()
                scope.launch {
                    databaseManager.setLastOpened(
                        record.hash, LocalDateTime.parse(PdfRecord.UNSET_DATE)
                    )
                    onChanged()
                }
            }
        }

        binding.optionsInfoButton.setOnClickListener { showFullProperties(item, record) }

        if (item.uri.scheme == "file") {
            binding.renameButton.setOnClickListener {
                dialog.dismiss()
                showRenameDialog(item, record)
            }
        } else {
            binding.renameButton.visibility = View.GONE
        }

        binding.deleteButton.setOnClickListener {
            dialog.dismiss()
            confirmDelete(item, record)
        }

        dialog.show()
    }

    private fun buildInfoLine(item: HomeItem, length: Int): String {
        val size = fileSizeBytes(item.uri)
        val sizeText = size?.let { String.format(Locale.US, "%.2f MB", it / (1024.0 * 1024.0)) }
        return when {
            length > 0 && sizeText != null ->
                activity.getString(R.string.home_pages_size_template, length, sizeText)
            length > 0 ->
                activity.resources.getQuantityString(R.plurals.home_pages, length, length)
            else -> sizeText.orEmpty()
        }
    }

    private fun bindStatus(binding: DialogRecordOptionsBinding, item: HomeItem, record: PdfRecord?) {
        binding.statusGroup.check(
            when (record?.reading ?: ReadingStatus.UNSET) {
                ReadingStatus.TO_READ -> R.id.statusToRead
                ReadingStatus.READING -> R.id.statusReading
                ReadingStatus.ON_HOLD -> R.id.statusOnHold
                ReadingStatus.COMPLETED -> R.id.statusCompleted
                ReadingStatus.ABANDONED -> R.id.statusAbandoned
                ReadingStatus.UNSET -> R.id.statusUnset
            }
        )
        var resolvedHash = record?.hash
        binding.statusGroup.setOnCheckedChangeListener { _, checkedId ->
            val status = when (checkedId) {
                R.id.statusToRead -> ReadingStatus.TO_READ
                R.id.statusReading -> ReadingStatus.READING
                R.id.statusOnHold -> ReadingStatus.ON_HOLD
                R.id.statusCompleted -> ReadingStatus.COMPLETED
                R.id.statusAbandoned -> ReadingStatus.ABANDONED
                else -> ReadingStatus.UNSET
            }
            scope.launch {
                val hash = resolvedHash ?: resolveContentHash(item) ?: return@launch
                resolvedHash = hash
                if (!databaseManager.hasRecord(hash)) {
                    databaseManager.saveRecordInBackground(newRecord(item, hash))
                }
                databaseManager.setReading(hash, status)
                onChanged()
            }
        }
    }

    private suspend fun resolveContentHash(item: HomeItem): String? {
        val path = item.uri.path ?: return null
        val known = libraryScanner.index.value.entries.find { it.path == path }?.hash
        if (known != null) {
            return known
        }
        val computed = withContext(Dispatchers.IO) { computeHash(File(path)) } ?: return null
        libraryScanner.onHashComputed(path, computed)
        return computed
    }

    private fun newRecord(item: HomeItem, hash: String): PdfRecord {
        return PdfRecord(
            hash,
            0,
            item.uri,
            item.length,
            fileNameOf(item, null),
            null,
            LocalDateTime.parse(PdfRecord.UNSET_DATE),
            ReadingStatus.UNSET,
            false,
        )
    }

    private fun showFullProperties(item: HomeItem, record: PdfRecord?) {
        scope.launch {
            val meta = withContext(Dispatchers.IO) { readDocumentMeta(item.uri) }
            showMetaDialog(activity, meta, fileNameOf(item, record), fileSizeBytes(item.uri))
        }
    }

    private fun fileNameOf(item: HomeItem, record: PdfRecord?): String {
        return record?.fileName
            ?: item.uri.path?.let { File(it).nameWithoutExtension }
            ?: item.title
    }

    private fun readDocumentMeta(uri: Uri): PdfDocument.Meta? {
        return runCatching {
            val core = PdfiumCore(activity)
            val fd = activity.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val document = try {
                core.newDocument(fd)
            } catch (throwable: Throwable) {
                runCatching { fd.close() }
                return null
            }
            try {
                core.getDocumentMeta(document)
            } finally {
                core.closeDocument(document)
            }
        }.getOrNull()
    }

    private fun fileSizeBytes(uri: Uri): Long? {
        return when (uri.scheme) {
            "file" -> uri.path?.let { File(it).length() }
            else -> runCatching {
                activity.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            }.getOrNull()
        }?.takeIf { it > 0 }
    }

    private fun showRenameDialog(item: HomeItem, record: PdfRecord?) {
        val binding = DialogRenameRecordBinding.inflate(activity.layoutInflater)
        binding.renameInput.setText(fileNameOf(item, record))

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.home_rename)
            .setView(binding.root)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newName = binding.renameInput.text?.toString()?.trim().orEmpty()
                if (newName.isNotEmpty() && !newName.contains(File.separatorChar)) {
                    performRename(item, record, newName)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performRename(item: HomeItem, record: PdfRecord?, newName: String) {
        scope.launch {
            val path = item.uri.path ?: return@launch
            val oldFile = File(path)
            val target = File(oldFile.parentFile, "$newName.pdf")

            if (target.exists()) {
                Toast.makeText(activity, R.string.home_rename_exists, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val renamed = withContext(Dispatchers.IO) { oldFile.renameTo(target) }
            if (!renamed) {
                Toast.makeText(activity, R.string.home_rename_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (record != null) {
                databaseManager.updateRecordIdentity(
                    record.hash, Uri.fromFile(target), newName, record.lastOpened
                )
                databaseManager.setDocumentTitle(record.hash, null)
            }
            libraryScanner.onFileRenamed(oldFile.absolutePath, target.absolutePath)
            onChanged()
        }
    }

    private fun confirmDelete(item: HomeItem, record: PdfRecord?) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.delete_dialog_title)
            .setMessage(
                activity.getString(R.string.home_delete_confirm_message, fileNameOf(item, record))
            )
            .setPositiveButton(R.string.delete) { _, _ -> performDelete(item, record) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performDelete(item: HomeItem, record: PdfRecord?) {
        scope.launch {
            val deleted = withContext(Dispatchers.IO) {
                if (item.uri.scheme == "file") {
                    item.uri.path?.let { File(it).delete() } ?: false
                } else {
                    runCatching {
                        DocumentsContract.deleteDocument(activity.contentResolver, item.uri)
                    }.getOrDefault(false)
                }
            }
            if (!deleted) {
                Toast.makeText(activity, R.string.home_delete_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (record != null) {
                databaseManager.removeRecords(listOf(record.hash))
            }
            coverCache.invalidate(item.coverKey)
            item.uri.path?.let { libraryScanner.onFileRemoved(it) }
            onChanged()
        }
    }

    companion object {
        private const val COVER_WIDTH_PX = 160
    }
}
