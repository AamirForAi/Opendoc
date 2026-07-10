package com.gitlab.mudlej.MjPdfReader.manager.backup

data class BackupData(
    val schemaVersion: Int = 0,
    val appVersionCode: Long? = null,
    val exportedAt: String? = null,
    val settings: List<BackupSetting>? = null,
    val pdfRecords: List<BackupPdfRecord>? = null,
    val userBookmarks: List<BackupUserBookmark>? = null,
)

data class BackupSetting(
    val key: String? = null,
    val type: String? = null,
    val value: String? = null,
    val values: List<String>? = null,
)

data class BackupPdfRecord(
    val hash: String? = null,
    val pageNumber: Int = 0,
    val length: Int = -1,
    val fileName: String? = null,
    val password: String? = null,
    val lastOpened: String? = null,
    val reading: String? = null,
    val favorite: Boolean = false,
    val cropMargins: String? = null,
    val cropMarginsVersion: Int = 0,
    val autoScrollSpeed: Int? = null,
    val readingDirectionOverride: String? = null,
    val detectedReadingDirection: String? = null,
    val documentTitle: String? = null,
    val uri: String? = null,
)

data class BackupUserBookmark(
    val fileHash: String? = null,
    val pageIndex: Int = -1,
    val label: String? = null,
    val createdAt: String? = null,
    val sortOrder: Int = -1,
)

data class ExportSummary(
    val settingsCount: Int,
    val recordsCount: Int,
    val bookmarksCount: Int,
)

data class ImportSummary(
    val settingsApplied: Int,
    val recordsInserted: Int,
    val recordsUpdated: Int,
    val bookmarksImported: Int,
)
