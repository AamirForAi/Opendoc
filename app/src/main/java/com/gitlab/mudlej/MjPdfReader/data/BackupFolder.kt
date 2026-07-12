// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.time.LocalDateTime

object BackupFolder {

    const val folderName = "MJ PDF"
    private const val retainedBackups = 10
    private val backupNameRegex = Regex("mj-pdf-backup-\\d{8}(-\\d{6})?\\.json")

    fun resolve(context: Context, treeUriString: String?): DocumentFile? {
        if (treeUriString.isNullOrBlank()) {
            return null
        }
        val treeUri = Uri.parse(treeUriString)
        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }
        if (!granted) {
            return null
        }
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        if (!root.isDirectory || !root.canWrite()) {
            return null
        }
        if (root.name == folderName) {
            return root
        }
        root.findFile(folderName)?.let { existing ->
            return if (existing.isDirectory) existing else null
        }
        return root.createDirectory(folderName)
    }

    fun describe(treeUriString: String?): String? {
        if (treeUriString.isNullOrBlank()) {
            return null
        }
        val path = runCatching {
            DocumentsContract.getTreeDocumentId(Uri.parse(treeUriString))
                .substringAfter(':')
                .trim('/')
        }.getOrNull() ?: return folderName
        return when {
            path.isBlank() -> folderName
            path.endsWith(folderName) -> path
            else -> "$path/$folderName"
        }
    }

    fun newBackupFileName(): String {
        val now = LocalDateTime.now()
        return "mj-pdf-backup-%04d%02d%02d-%02d%02d%02d.json".format(
            now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute, now.second)
    }

    fun enforceRetention(folder: DocumentFile) {
        folder.listFiles()
            .filter { it.isFile && it.name?.matches(backupNameRegex) == true }
            .sortedByDescending { it.name }
            .drop(retainedBackups)
            .forEach { it.delete() }
    }
}
