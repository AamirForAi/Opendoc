package com.gitlab.mudlej.MjPdfReader.data

import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfAnnotationSaveDestination
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class PdfRepository(private val database: AppDatabase) {

    suspend fun findAllRecords(): List<PdfRecord> {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findAll()
        }
    }

    suspend fun upsertRecords(records: List<PdfRecord>) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().insertAll(records)
        }
    }

    suspend fun findUserBookmarks(fileHash: String): List<UserBookmark> {
        return withContext(Dispatchers.IO) {
            database.userBookmarkDao().findByHash(fileHash)
        }
    }

    suspend fun findAllUserBookmarks(): List<UserBookmark> {
        return withContext(Dispatchers.IO) {
            database.userBookmarkDao().findAll()
        }
    }

    suspend fun addUserBookmark(bookmark: UserBookmark) {
        withContext(Dispatchers.IO) {
            val dao = database.userBookmarkDao()
            val orderedBookmark = if (bookmark.sortOrder < 0) {
                bookmark.copy(sortOrder = dao.nextSortOrder(bookmark.fileHash))
            } else {
                bookmark
            }
            dao.upsert(orderedBookmark)
        }
    }

    suspend fun upsertUserBookmarks(bookmarks: List<UserBookmark>) {
        withContext(Dispatchers.IO) {
            database.userBookmarkDao().upsertAll(bookmarks)
        }
    }

    suspend fun removeUserBookmark(fileHash: String, pageIndex: Int) {
        withContext(Dispatchers.IO) {
            database.userBookmarkDao().delete(fileHash, pageIndex)
        }
    }

    suspend fun setUserBookmarkLabel(fileHash: String, pageIndex: Int, label: String?) {
        withContext(Dispatchers.IO) {
            database.userBookmarkDao().updateLabel(fileHash, pageIndex, label)
        }
    }

    suspend fun setUserBookmarkOrder(bookmarks: List<UserBookmark>) {
        withContext(Dispatchers.IO) {
            val dao = database.userBookmarkDao()
            bookmarks.forEach { bookmark ->
                dao.updateSortOrder(bookmark.fileHash, bookmark.pageIndex, bookmark.sortOrder)
            }
        }
    }

    suspend fun findRecord(fileHash: String): PdfRecord? {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findByHash(fileHash)
        }
    }

    suspend fun updateRecordIdentity(
        fileHash: String,
        uri: android.net.Uri,
        fileName: String,
        lastOpened: LocalDateTime,
    ) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateIdentity(fileHash, uri, fileName, lastOpened)
        }
    }

    suspend fun findAnnotationSaveDestinationBySourceKey(sourceKey: String): PdfAnnotationSaveDestination? {
        return withContext(Dispatchers.IO) {
            database.pdfAnnotationSaveDestinationDao().findBySourceKey(sourceKey)
        }
    }

    suspend fun findAnnotationSaveDestinationByDestinationUri(destinationUri: String): PdfAnnotationSaveDestination? {
        return withContext(Dispatchers.IO) {
            database.pdfAnnotationSaveDestinationDao().findByDestinationUri(destinationUri)
        }
    }

    suspend fun findAnnotationSaveDestinationByLastSavedHash(hash: String): PdfAnnotationSaveDestination? {
        return withContext(Dispatchers.IO) {
            database.pdfAnnotationSaveDestinationDao().findByLastSavedHash(hash)
        }
    }

    suspend fun saveAnnotationSaveDestination(destination: PdfAnnotationSaveDestination) {
        withContext(Dispatchers.IO) {
            database.pdfAnnotationSaveDestinationDao().upsert(destination)
        }
    }

    suspend fun saveRecordInBackground(pdfRecord: PdfRecord) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().insert(pdfRecord)
        }
    }

    suspend fun copyOrUpdateRecordIdentity(
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

    suspend fun findPageNumber(fileHash: String): Int {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findSavedPage(fileHash) ?: 0
        }
    }

    suspend fun findPdfPassword(fileHash: String): String? {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findPdfPassword(fileHash)
        }
    }

    suspend fun findCropMargins(fileHash: String, version: Int): String? {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findCropMargins(fileHash, version)
        }
    }

    suspend fun findAutoScrollSpeed(fileHash: String): Int? {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findAutoScrollSpeed(fileHash)
        }
    }

    suspend fun findReadingDirectionOverride(fileHash: String): String? {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findReadingDirectionOverride(fileHash)
        }
    }

    suspend fun findDetectedReadingDirection(fileHash: String): String? {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findDetectedReadingDirection(fileHash)
        }
    }

    suspend fun setPageNumber(fileHash: String, page: Int) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updatePageNumber(fileHash, page)
        }
    }

    suspend fun hasRecord(fileHash: String): Boolean {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().hasRecord(fileHash)
        }
    }

    suspend fun setLastOpened(fileHash: String, lastOpened: LocalDateTime) {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateLastOpened(fileHash, lastOpened)
        }
    }

    suspend fun removeRecord(record: PdfRecord) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().delete(record)
        }
    }

    suspend fun removeRecords(fileHashes: List<String>) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().deleteByHashes(fileHashes)
        }
    }

    suspend fun setFavorite(fileHash: String, favorite: Boolean) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateFavorite(fileHash, favorite)
        }
    }

    suspend fun setFavoriteBatch(fileHashes: List<String>, favorite: Boolean) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateFavoriteBatch(fileHashes, favorite)
        }
    }

    suspend fun setHidden(fileHash: String, hidden: Boolean) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateHidden(fileHash, hidden)
        }
    }

    suspend fun setReading(fileHash: String, readingStatus: ReadingStatus) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateReading(fileHash, readingStatus)
        }
    }

    suspend fun setReadingBatch(fileHashes: List<String>, readingStatus: ReadingStatus) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateReadingBatch(fileHashes, readingStatus)
        }
    }

    suspend fun findAllScannedPdfs(): List<ScannedPdfEntry> {
        return withContext(Dispatchers.IO) {
            database.scannedPdfEntryDao().findAll()
        }
    }

    suspend fun findScannedPdfsByHash(hash: String): List<ScannedPdfEntry> {
        return withContext(Dispatchers.IO) {
            database.scannedPdfEntryDao().findByHash(hash)
        }
    }

    suspend fun upsertScannedPdfs(entries: List<ScannedPdfEntry>) {
        withContext(Dispatchers.IO) {
            database.scannedPdfEntryDao().upsertAll(entries)
        }
    }

    suspend fun pruneScannedPdfs(paths: List<String>) {
        withContext(Dispatchers.IO) {
            database.scannedPdfEntryDao().deleteByPaths(paths)
        }
    }

    suspend fun updateScannedPdfPath(oldPath: String, newPath: String) {
        withContext(Dispatchers.IO) {
            database.scannedPdfEntryDao().updatePath(oldPath, newPath)
        }
    }

    suspend fun setPassword(fileHash: String, password: String) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updatePassword(fileHash, password)
        }
    }

    suspend fun setDocumentTitle(fileHash: String, title: String?) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateDocumentTitle(fileHash, title)
        }
    }

    suspend fun setCropMargins(fileHash: String, cropMargins: String, version: Int) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateCropMargins(fileHash, cropMargins, version)
        }
    }

    suspend fun setAutoScrollSpeed(fileHash: String, speed: Int) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateAutoScrollSpeed(fileHash, speed)
        }
    }

    suspend fun setReadingDirectionOverride(fileHash: String, direction: String?) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateReadingDirectionOverride(fileHash, direction)
        }
    }

    suspend fun setDetectedReadingDirection(fileHash: String, direction: String) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateDetectedReadingDirection(fileHash, direction)
        }
    }

}
