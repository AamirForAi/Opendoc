// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import android.content.Context
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

    suspend fun export(uri: Uri, includePasswords: Boolean): ExportSummary = withContext(Dispatchers.IO) {
        val settings = collectSettings()
        val records = pdfRepository.findAllRecords().map { it.toBackup(includePasswords) }
        val bookmarks = pdfRepository.findAllUserBookmarks().map { it.toBackup() }
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
        ExportSummary(settings.size, records.size, bookmarks.size)
    }

    suspend fun import(uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
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
        val settingsApplied = applySettings(data.settings.orEmpty())
        val (recordsInserted, recordsUpdated) = mergeRecords(data.pdfRecords.orEmpty())
        val bookmarksImported = mergeBookmarks(data.userBookmarks.orEmpty())
        ImportSummary(settingsApplied, recordsInserted, recordsUpdated, bookmarksImported)
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

    private fun applySettings(settings: List<BackupSetting>): Int {
        val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
        var applied = 0
        settings.forEach { setting ->
            val key = setting.key ?: return@forEach
            if (key in excludedSettingKeys) {
                return@forEach
            }
            val stored = when (setting.type) {
                "boolean" -> setting.value?.toBooleanStrictOrNull()?.also { editor.putBoolean(key, it) } != null
                "int" -> setting.value?.toIntOrNull()?.also { editor.putInt(key, it) } != null
                "long" -> setting.value?.toLongOrNull()?.also { editor.putLong(key, it) } != null
                "float" -> setting.value?.toFloatOrNull()?.also { editor.putFloat(key, it) } != null
                "string" -> setting.value?.also { editor.putString(key, it) } != null
                "stringSet" -> setting.values?.also { editor.putStringSet(key, it.toSet()) } != null
                else -> false
            }
            if (stored) {
                applied++
            }
        }
        editor.apply()
        return applied
    }

    private suspend fun mergeRecords(records: List<BackupPdfRecord>): Pair<Int, Int> {
        var inserted = 0
        var updated = 0
        val existingByHash = pdfRepository.findAllRecords().associateBy { it.hash }
        val toSave = mutableListOf<PdfRecord>()
        records.forEach { backup ->
            val hash = backup.hash?.takeIf { it.isNotBlank() } ?: return@forEach
            val lastOpened = parseDate(backup.lastOpened) ?: LocalDateTime.MIN
            val existing = existingByHash[hash]
            if (existing == null) {
                toSave.add(
                    PdfRecord(
                        hash = hash,
                        pageNumber = backup.pageNumber,
                        uri = Uri.parse(backup.uri.orEmpty()),
                        length = backup.length,
                        fileName = backup.fileName.orEmpty(),
                        password = backup.password,
                        lastOpened = lastOpened,
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
                    )
                )
                inserted++
            } else if (lastOpened.isAfter(existing.lastOpened)) {
                toSave.add(
                    existing.copy(
                        pageNumber = backup.pageNumber,
                        password = backup.password ?: existing.password,
                        lastOpened = lastOpened,
                        reading = parseReadingStatus(backup.reading, existing.reading),
                        favorite = backup.favorite || existing.favorite,
                        cropMargins = backup.cropMargins ?: existing.cropMargins,
                        cropMarginsVersion = if (backup.cropMargins != null) backup.cropMarginsVersion else existing.cropMarginsVersion,
                        autoScrollSpeed = backup.autoScrollSpeed ?: existing.autoScrollSpeed,
                        readingDirectionOverride = backup.readingDirectionOverride ?: existing.readingDirectionOverride,
                        detectedReadingDirection = backup.detectedReadingDirection ?: existing.detectedReadingDirection,
                        documentTitle = backup.documentTitle ?: existing.documentTitle,
                        textModeJoinParagraphs = backup.textModeJoinParagraphs ?: existing.textModeJoinParagraphs,
                        textModeDetectHeadings = backup.textModeDetectHeadings ?: existing.textModeDetectHeadings,
                        textModeCodeBlocks = backup.textModeCodeBlocks ?: existing.textModeCodeBlocks,
                    )
                )
                updated++
            }
        }
        if (toSave.isNotEmpty()) {
            pdfRepository.upsertRecords(toSave)
        }
        return inserted to updated
    }

    private suspend fun mergeBookmarks(bookmarks: List<BackupUserBookmark>): Int {
        val existingByKey = pdfRepository.findAllUserBookmarks().associateBy { it.fileHash to it.pageIndex }
        val toSave = bookmarks.mapNotNull { backup ->
            val hash = backup.fileHash?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (backup.pageIndex < 0) {
                return@mapNotNull null
            }
            val existing = existingByKey[hash to backup.pageIndex]
            when {
                existing == null -> UserBookmark(
                    fileHash = hash,
                    pageIndex = backup.pageIndex,
                    label = backup.label,
                    createdAt = parseDate(backup.createdAt) ?: LocalDateTime.now(),
                    sortOrder = backup.sortOrder.takeIf { it >= 0 } ?: backup.pageIndex,
                )
                existing.label == null && backup.label != null -> existing.copy(label = backup.label)
                else -> null
            }
        }
        if (toSave.isNotEmpty()) {
            pdfRepository.upsertUserBookmarks(toSave)
        }
        return toSave.size
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
        )

        fun defaultBackupFileName(): String {
            val now = LocalDateTime.now()
            return "mj-pdf-backup-%04d%02d%02d.json".format(now.year, now.monthValue, now.dayOfMonth)
        }
    }
}
