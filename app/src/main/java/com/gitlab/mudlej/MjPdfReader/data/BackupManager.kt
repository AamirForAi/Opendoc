// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.preference.PreferenceManager
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime

class BackupManager(
    private val context: Context,
    private val pdfRepository: PdfRepository,
) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun export(uri: Uri, options: BackupExportOptions): ExportSummary = withContext(Dispatchers.IO) {
        val settings = if (options.includeSettings) collectSettings() else null
        val records = if (options.includeHistory) {
            pdfRepository.findAllRecords().map { it.toBackup(options.includePasswords) }
        } else {
            null
        }
        val bookmarks = if (options.includeHistory) {
            pdfRepository.findAllUserBookmarks().map { it.toBackup() }
        } else {
            null
        }
        val data = BackupData(
            schemaVersion = SCHEMA_VERSION,
            appVersionCode = appVersionCode(),
            exportedAt = LocalDateTime.now().toString(),
            settings = settings,
            pdfRecords = records,
            userBookmarks = bookmarks,
        )
        val json = gson.toJson(data)
        val output = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("Cannot open the selected file for writing")
        output.use { stream ->
            stream.write(json.toByteArray(Charsets.UTF_8))
        }
        ExportSummary(settings?.size ?: 0, records?.size ?: 0, bookmarks?.size ?: 0)
    }

    suspend fun parse(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: throw IOException("Cannot open the selected file for reading")
        val data = try {
            gson.fromJson(json, BackupData::class.java)
        } catch (exception: Exception) {
            null
        } ?: throw IOException("The selected file is not a valid backup")
        if (data.schemaVersion < 1) {
            throw IOException("The selected file is not a valid backup")
        }
        if (data.schemaVersion > SCHEMA_VERSION) {
            throw IOException("The backup was created by a newer app version")
        }
        data
    }

    suspend fun importReplace(data: BackupData, historyCleaner: HistoryCleaner) = withContext(Dispatchers.IO) {
        data.settings?.let { replaceSettings(it) }
        if (data.includesHistory) {
            historyCleaner.clearReadingHistory()
            historyCleaner.clearBookmarks()
            historyCleaner.clearAnnotationJournalsAndSignature()
            insertRecords(data.pdfRecords.orEmpty())
            insertBookmarks(data.userBookmarks.orEmpty())
        }
    }

    private fun collectSettings(): List<BackupSetting> {
        val all = PreferenceManager.getDefaultSharedPreferences(context).all
        return all.mapNotNull { (key, value) ->
            if (key in excludedSettingKeys || value == null) {
                return@mapNotNull null
            }
            when (value) {
                is Boolean -> BackupSetting(key, "boolean", value.toString())
                is Int -> BackupSetting(key, "int", value.toString())
                is Long -> BackupSetting(key, "long", value.toString())
                is Float -> BackupSetting(key, "float", value.toString())
                is String -> BackupSetting(key, "string", value)
                is Set<*> -> BackupSetting(key, "stringSet", null, value.filterIsInstance<String>())
                else -> null
            }
        }.sortedBy { it.key }
    }

    private fun replaceSettings(settings: List<BackupSetting>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val preserved = prefs.all.filterKeys { it in excludedSettingKeys }
        val editor = prefs.edit().clear()
        preserved.forEach { (key, value) -> putSetting(editor, key, value) }
        settings.forEach { setting ->
            val key = setting.key ?: return@forEach
            if (key in excludedSettingKeys) {
                return@forEach
            }
            when (setting.type) {
                "boolean" -> setting.value?.toBooleanStrictOrNull()?.let { editor.putBoolean(key, it) }
                "int" -> setting.value?.toIntOrNull()?.let { editor.putInt(key, it) }
                "long" -> setting.value?.toLongOrNull()?.let { editor.putLong(key, it) }
                "float" -> setting.value?.toFloatOrNull()?.let { editor.putFloat(key, it) }
                "string" -> setting.value?.let { editor.putString(key, it) }
                "stringSet" -> setting.values?.let { editor.putStringSet(key, it.toSet()) }
            }
        }
        @Suppress("ApplySharedPref")
        editor.commit()
    }

    private fun putSetting(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }

    private suspend fun insertRecords(records: List<BackupPdfRecord>) {
        val toSave = records.mapNotNull { backup ->
            val hash = backup.hash?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PdfRecord(
                hash = hash,
                pageNumber = backup.pageNumber,
                uri = Uri.parse(backup.uri.orEmpty()),
                length = backup.length,
                fileName = backup.fileName.orEmpty(),
                password = backup.password,
                lastOpened = parseDate(backup.lastOpened) ?: LocalDateTime.MIN,
                reading = parseReadingStatus(backup.reading),
                favorite = backup.favorite,
                cropMargins = backup.cropMargins,
                cropMarginsVersion = backup.cropMarginsVersion,
                autoScrollSpeed = backup.autoScrollSpeed,
                readingDirectionOverride = backup.readingDirectionOverride,
                detectedReadingDirection = backup.detectedReadingDirection,
                documentTitle = backup.documentTitle,
                textModeJoinParagraphs = backup.textModeJoinParagraphs,
                textModeDetectHeadings = backup.textModeDetectHeadings,
                textModeCodeBlocks = backup.textModeCodeBlocks,
                hidden = backup.hidden,
            )
        }
        if (toSave.isNotEmpty()) {
            pdfRepository.upsertRecords(toSave)
        }
    }

    private suspend fun insertBookmarks(bookmarks: List<BackupUserBookmark>) {
        val toSave = bookmarks.mapNotNull { backup ->
            val hash = backup.fileHash?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (backup.pageIndex < 0) {
                return@mapNotNull null
            }
            UserBookmark(
                fileHash = hash,
                pageIndex = backup.pageIndex,
                label = backup.label,
                createdAt = parseDate(backup.createdAt) ?: LocalDateTime.now(),
                sortOrder = backup.sortOrder.takeIf { it >= 0 } ?: backup.pageIndex,
            )
        }
        if (toSave.isNotEmpty()) {
            pdfRepository.upsertUserBookmarks(toSave)
        }
    }

    private fun PdfRecord.toBackup(includePasswords: Boolean): BackupPdfRecord {
        return BackupPdfRecord(
            hash = hash,
            pageNumber = pageNumber,
            length = length,
            fileName = fileName,
            password = if (includePasswords) password else null,
            lastOpened = lastOpened.toString(),
            reading = reading.name,
            favorite = favorite,
            cropMargins = cropMargins,
            cropMarginsVersion = cropMarginsVersion,
            autoScrollSpeed = autoScrollSpeed,
            readingDirectionOverride = readingDirectionOverride,
            detectedReadingDirection = detectedReadingDirection,
            documentTitle = documentTitle,
            uri = uri.toString(),
            textModeJoinParagraphs = textModeJoinParagraphs,
            textModeDetectHeadings = textModeDetectHeadings,
            textModeCodeBlocks = textModeCodeBlocks,
            hidden = hidden,
        )
    }

    private fun UserBookmark.toBackup(): BackupUserBookmark {
        return BackupUserBookmark(
            fileHash = fileHash,
            pageIndex = pageIndex,
            label = label,
            createdAt = createdAt.toString(),
            sortOrder = sortOrder,
        )
    }

    private fun parseDate(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) {
            return null
        }
        return runCatching { LocalDateTime.parse(value) }.getOrNull()
    }

    private fun parseReadingStatus(value: String?, fallback: ReadingStatus = ReadingStatus.UNSET): ReadingStatus {
        return ReadingStatus.entries.firstOrNull { it.name == value } ?: fallback
    }

    private fun appVersionCode(): Long {
        return runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        }.getOrDefault(0L)
    }

    companion object {
        const val SCHEMA_VERSION = 1

        private val excludedSettingKeys = setOf(
            Preferences.firstInstallKey,
            Preferences.showFeaturesDialogKey,
            Preferences.backupFolderTreeUriKey,
            Preferences.autoBackupLastRunKey,
            Preferences.autoBackupLastErrorKey,
        )
    }
}
