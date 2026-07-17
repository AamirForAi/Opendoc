// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.io.restartApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object BackupImportPipeline {

    @Volatile
    private var running = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(
        appContext: Context,
        data: BackupData,
        createSafety: Boolean,
        onRejected: (String) -> Unit,
    ): Boolean {
        if (running) {
            return false
        }
        running = true
        val preferences = Preferences(PreferenceManager.getDefaultSharedPreferences(appContext))
        val backupManager = BackupManager(appContext, PdfRepository(AppDatabase.getInstance(appContext)))
        scope.launch {
            if (createSafety) {
                val safetyError = writeSafetySnapshot(appContext, backupManager)
                if (safetyError != null) {
                    running = false
                    withContext(Dispatchers.Main) { onRejected(safetyError) }
                    return@launch
                }
            }
            try {
                val summary = backupManager.importReplace(data)
                preferences.setImportResultPending(renderSummary(appContext, data, summary))
            } catch (exception: Exception) {
                preferences.setImportResultPending(
                    appContext.getString(
                        R.string.backup_import_failed_after_wipe,
                        BackupException.render(appContext, exception),
                    )
                )
            }
            AutoBackupScheduler.cancel(appContext)
            running = false
            withContext(Dispatchers.Main) { restartApplication(appContext) }
        }
        return true
    }

    private suspend fun writeSafetySnapshot(appContext: Context, backupManager: BackupManager): String? {
        return try {
            val dir = BackupFolder.safetyDir(appContext)
            if (!dir.isDirectory && !dir.mkdirs()) {
                throw BackupException(R.string.backup_error_create_file)
            }
            val folder = DocumentFile.fromFile(dir)
            backupManager.export(
                folder,
                BackupFolder.newSafetySnapshotName(),
                BackupExportOptions(includeSettings = true, includeHistory = true, includePasswords = true),
            )
            BackupFolder.pruneSafetySnapshots(appContext)
            null
        } catch (exception: Exception) {
            appContext.getString(
                R.string.backup_import_safety_aborted,
                BackupException.render(appContext, exception),
            )
        }
    }

    private fun renderSummary(appContext: Context, data: BackupData, summary: ImportSummary): String {
        return buildList {
            add(appContext.getString(R.string.backup_import_finished))
            if (data.settings != null) {
                add(appContext.getString(R.string.backup_import_done_settings, summary.settingsCount))
            }
            if (data.includesHistory) {
                add(
                    appContext.getString(
                        R.string.backup_import_done_history,
                        summary.recordsCount,
                        summary.bookmarksCount,
                    )
                )
            }
            if (summary.skippedSettingsCount > 0) {
                add(appContext.getString(R.string.backup_import_skipped_settings, summary.skippedSettingsCount))
            }
        }.joinToString("\n")
    }
}
