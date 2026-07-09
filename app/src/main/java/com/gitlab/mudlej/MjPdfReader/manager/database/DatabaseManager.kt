package com.gitlab.mudlej.MjPdfReader.manager.database

import com.gitlab.mudlej.MjPdfReader.enums.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.repository.PdfAnnotationSaveDestination
import com.gitlab.mudlej.MjPdfReader.repository.PdfRecord
import com.gitlab.mudlej.MjPdfReader.repository.ScannedPdfCache
import java.time.LocalDateTime

interface DatabaseManager {

    suspend fun findAllRecords(): List<PdfRecord>

    suspend fun findRecord(fileHash: String): PdfRecord?

    suspend fun updateRecordIdentity(
        fileHash: String,
        uri: android.net.Uri,
        fileName: String,
        lastOpened: LocalDateTime,
    )

    suspend fun findAnnotationSaveDestinationBySourceKey(sourceKey: String): PdfAnnotationSaveDestination?

    suspend fun findAnnotationSaveDestinationByDestinationUri(destinationUri: String): PdfAnnotationSaveDestination?

    suspend fun findAnnotationSaveDestinationByLastSavedHash(hash: String): PdfAnnotationSaveDestination?

    suspend fun saveAnnotationSaveDestination(destination: PdfAnnotationSaveDestination)

    suspend fun saveRecordInBackground(pdfRecord: PdfRecord)

    suspend fun copyOrUpdateRecordIdentity(
        oldHash: String,
        newHash: String,
        sourceUri: android.net.Uri,
        destinationUri: android.net.Uri,
        fileName: String,
    )

    suspend fun findPageNumber(fileHash: String): Int

    suspend fun findPdfPassword(fileHash: String): String?

    suspend fun findCropMargins(fileHash: String, version: Int): String?

    suspend fun findAutoScrollSpeed(fileHash: String): Int?

    suspend fun findReadingDirectionOverride(fileHash: String): String?

    suspend fun findDetectedReadingDirection(fileHash: String): String?

    suspend fun setPageNumber(fileHash: String, page: Int)

    suspend fun hasRecord(fileHash: String): Boolean

    suspend fun setLastOpened(fileHash: String, lastOpened: LocalDateTime)

    suspend fun removeRecord(record: PdfRecord)

    suspend fun removeRecords(fileHashes: List<String>)

    suspend fun setFavorite(fileHash: String, favorite: Boolean)

    suspend fun setFavoriteBatch(fileHashes: List<String>, favorite: Boolean)

    suspend fun setReading(fileHash: String, readingStatus: ReadingStatus)

    suspend fun setReadingBatch(fileHashes: List<String>, readingStatus: ReadingStatus)

    suspend fun findAllScannedPdfs(): List<ScannedPdfCache>

    suspend fun findScannedPdfsByHash(hash: String): List<ScannedPdfCache>

    suspend fun upsertScannedPdfs(entries: List<ScannedPdfCache>)

    suspend fun pruneScannedPdfs(paths: List<String>)

    suspend fun updateScannedPdfPath(oldPath: String, newPath: String)

    suspend fun setPassword(fileHash: String, password: String)

    suspend fun setDocumentTitle(fileHash: String, title: String?)

    suspend fun setCropMargins(fileHash: String, cropMargins: String, version: Int)

    suspend fun setAutoScrollSpeed(fileHash: String, speed: Int)

    suspend fun setReadingDirectionOverride(fileHash: String, direction: String?)

    suspend fun setDetectedReadingDirection(fileHash: String, direction: String)

}
