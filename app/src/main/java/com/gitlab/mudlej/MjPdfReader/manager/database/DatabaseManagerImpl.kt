package com.gitlab.mudlej.MjPdfReader.manager.database

import com.gitlab.mudlej.MjPdfReader.enums.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.repository.AppDatabase
import com.gitlab.mudlej.MjPdfReader.repository.PdfAnnotationSaveDestination
import com.gitlab.mudlej.MjPdfReader.repository.PdfRecord
import com.gitlab.mudlej.MjPdfReader.repository.ScannedPdfCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class DatabaseManagerImpl(private val database: AppDatabase): DatabaseManager {

    override suspend fun findAllRecords(): List<PdfRecord> {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findAll()
        }
    }

    override suspend fun findRecord(fileHash: String): PdfRecord? {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findByHash(fileHash)
        }
    }

    override suspend fun updateRecordIdentity(
        fileHash: String,
        uri: android.net.Uri,
        fileName: String,
        lastOpened: LocalDateTime,
    ) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateIdentity(fileHash, uri, fileName, lastOpened)
        }
    }

    override suspend fun findAnnotationSaveDestinationBySourceKey(sourceKey: String): PdfAnnotationSaveDestination? {
        return withContext(Dispatchers.IO) {
            database.pdfAnnotationSaveDestinationDao().findBySourceKey(sourceKey)
        }
    }

    override suspend fun findAnnotationSaveDestinationByDestinationUri(destinationUri: String): PdfAnnotationSaveDestination? {
        return withContext(Dispatchers.IO) {
            database.pdfAnnotationSaveDestinationDao().findByDestinationUri(destinationUri)
        }
    }

    override suspend fun findAnnotationSaveDestinationByLastSavedHash(hash: String): PdfAnnotationSaveDestination? {
        return withContext(Dispatchers.IO) {
            database.pdfAnnotationSaveDestinationDao().findByLastSavedHash(hash)
        }
    }

    override suspend fun saveAnnotationSaveDestination(destination: PdfAnnotationSaveDestination) {
        withContext(Dispatchers.IO) {
            database.pdfAnnotationSaveDestinationDao().upsert(destination)
        }
    }

    override suspend fun saveRecordInBackground(pdfRecord: PdfRecord) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().insert(pdfRecord)
        }
    }

    override suspend fun copyOrUpdateRecordIdentity(
        oldHash: String,
        newHash: String,
        sourceUri: android.net.Uri,
        destinationUri: android.net.Uri,
        fileName: String,
    ) {
        withContext(Dispatchers.IO) {
            val dao = database.pdfRecordDao()
            val now = LocalDateTime.now()
            val replacingSourceFile = sourceUri.toString() == destinationUri.toString()
            if (oldHash == newHash) {
                if (replacingSourceFile) {
                    dao.updateIdentity(oldHash, destinationUri, fileName, now)
                }
                return@withContext
            }

            val source = dao.findByHash(oldHash)
            if (source != null) {
                dao.insert(
                    source.copy(
                        hash = newHash,
                        uri = destinationUri,
                        fileName = fileName,
                        lastOpened = now,
                    )
                )
                if (replacingSourceFile) {
                    dao.deleteByHash(oldHash)
                }
            }
        }
    }

    override suspend fun findPageNumber(fileHash: String): Int {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findSavedPage(fileHash) ?: 0
        }
    }

    override suspend fun findPdfPassword(fileHash: String): String? {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findPdfPassword(fileHash)
        }
    }

    override suspend fun findCropMargins(fileHash: String, version: Int): String? {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findCropMargins(fileHash, version)
        }
    }

    override suspend fun findAutoScrollSpeed(fileHash: String): Int? {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findAutoScrollSpeed(fileHash)
        }
    }

    override suspend fun findReadingDirectionOverride(fileHash: String): String? {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findReadingDirectionOverride(fileHash)
        }
    }

    override suspend fun findDetectedReadingDirection(fileHash: String): String? {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findDetectedReadingDirection(fileHash)
        }
    }

    override suspend fun setPageNumber(fileHash: String, page: Int) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updatePageNumber(fileHash, page)
        }
    }

    override suspend fun hasRecord(fileHash: String): Boolean {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().hasRecord(fileHash)
        }
    }

    override suspend fun setLastOpened(fileHash: String, lastOpened: LocalDateTime) {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateLastOpened(fileHash, lastOpened)
        }
    }

    override suspend fun removeRecord(record: PdfRecord) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().delete(record)
        }
    }

    override suspend fun removeRecords(fileHashes: List<String>) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().deleteByHashes(fileHashes)
        }
    }

    override suspend fun setFavorite(fileHash: String, favorite: Boolean) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateFavorite(fileHash, favorite)
        }
    }

    override suspend fun setFavoriteBatch(fileHashes: List<String>, favorite: Boolean) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateFavoriteBatch(fileHashes, favorite)
        }
    }

    override suspend fun setReading(fileHash: String, readingStatus: ReadingStatus) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateReading(fileHash, readingStatus)
        }
    }

    override suspend fun setReadingBatch(fileHashes: List<String>, readingStatus: ReadingStatus) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateReadingBatch(fileHashes, readingStatus)
        }
    }

    override suspend fun findAllScannedPdfs(): List<ScannedPdfCache> {
        return withContext(Dispatchers.IO) {
            database.scannedPdfCacheDao().findAll()
        }
    }

    override suspend fun findScannedPdfsByHash(hash: String): List<ScannedPdfCache> {
        return withContext(Dispatchers.IO) {
            database.scannedPdfCacheDao().findByHash(hash)
        }
    }

    override suspend fun upsertScannedPdfs(entries: List<ScannedPdfCache>) {
        withContext(Dispatchers.IO) {
            database.scannedPdfCacheDao().upsertAll(entries)
        }
    }

    override suspend fun pruneScannedPdfs(paths: List<String>) {
        withContext(Dispatchers.IO) {
            database.scannedPdfCacheDao().deleteByPaths(paths)
        }
    }

    override suspend fun updateScannedPdfPath(oldPath: String, newPath: String) {
        withContext(Dispatchers.IO) {
            database.scannedPdfCacheDao().updatePath(oldPath, newPath)
        }
    }

    override suspend fun setPassword(fileHash: String, password: String) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updatePassword(fileHash, password)
        }
    }

    override suspend fun setDocumentTitle(fileHash: String, title: String?) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateDocumentTitle(fileHash, title)
        }
    }

    override suspend fun setCropMargins(fileHash: String, cropMargins: String, version: Int) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateCropMargins(fileHash, cropMargins, version)
        }
    }

    override suspend fun setAutoScrollSpeed(fileHash: String, speed: Int) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateAutoScrollSpeed(fileHash, speed)
        }
    }

    override suspend fun setReadingDirectionOverride(fileHash: String, direction: String?) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateReadingDirectionOverride(fileHash, direction)
        }
    }

    override suspend fun setDetectedReadingDirection(fileHash: String, direction: String) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateDetectedReadingDirection(fileHash, direction)
        }
    }

}
