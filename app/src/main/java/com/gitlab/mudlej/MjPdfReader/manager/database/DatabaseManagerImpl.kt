package com.gitlab.mudlej.MjPdfReader.manager.database

import com.gitlab.mudlej.MjPdfReader.enums.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.repository.AppDatabase
import com.gitlab.mudlej.MjPdfReader.repository.PdfRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class DatabaseManagerImpl(private val database: AppDatabase): DatabaseManager {

    override suspend fun findAllRecords(): List<PdfRecord> {
        return withContext(Dispatchers.IO) {
            database.pdfRecordDao().findAll()
        }
    }

    override suspend fun saveRecordInBackground(pdfRecord: PdfRecord) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().insert(pdfRecord)
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

    override suspend fun setFavorite(fileHash: String, favorite: Boolean) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateFavorite(fileHash, favorite)
        }
    }

    override suspend fun setReading(fileHash: String, readingStatus: ReadingStatus) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updateReading(fileHash, readingStatus)
        }
    }

    override suspend fun setPassword(fileHash: String, password: String) {
        withContext(Dispatchers.IO) {
            database.pdfRecordDao().updatePassword(fileHash, password)
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
