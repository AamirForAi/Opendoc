// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object AutoBackupScheduler {

    private const val workTag = "autoBackupWork"

    fun schedule(context: Context, hour: Int, minute: Int) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(workTag)
        workManager.enqueue(nextRequest(hour, minute))
    }

    fun scheduleNext(context: Context, hour: Int, minute: Int) {
        WorkManager.getInstance(context).enqueue(nextRequest(hour, minute))
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(workTag)
    }

    private fun nextRequest(hour: Int, minute: Int): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInitialDelay(millisUntilNext(hour, minute), TimeUnit.MILLISECONDS)
            .addTag(workTag)
            .build()
    }

    private fun millisUntilNext(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, minute)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).toMillis()
    }
}

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val preferences = Preferences(PreferenceManager.getDefaultSharedPreferences(applicationContext))
        if (!preferences.getAutoBackupEnabled()) {
            return Result.success()
        }
        try {
            val folder = BackupFolder.resolve(applicationContext, preferences.getBackupFolderTreeUri())
                ?: throw IOException("The backup folder is unavailable")
            val file = folder.createFile("application/json", BackupFolder.newBackupFileName())
                ?: throw IOException("Cannot create a file in the backup folder")
            val backupManager = BackupManager(
                applicationContext,
                PdfRepository(AppDatabase.getInstance(applicationContext)),
            )
            backupManager.export(
                file.uri,
                BackupExportOptions(includeSettings = true, includeHistory = true, includePasswords = false),
            )
            BackupFolder.enforceRetention(folder)
            preferences.setAutoBackupLastResult(System.currentTimeMillis(), null)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            preferences.setAutoBackupLastResult(
                System.currentTimeMillis(),
                exception.localizedMessage ?: exception.javaClass.simpleName,
            )
        }
        if (preferences.getAutoBackupEnabled()) {
            AutoBackupScheduler.scheduleNext(
                applicationContext,
                preferences.getAutoBackupHour(),
                preferences.getAutoBackupMinute(),
            )
        }
        return Result.success()
    }
}
