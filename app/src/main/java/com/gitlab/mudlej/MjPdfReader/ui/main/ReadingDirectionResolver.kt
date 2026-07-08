package com.gitlab.mudlej.MjPdfReader.ui.main

import android.app.Activity
import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.enums.ReadingDirection
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManager

class ReadingDirectionResolver(
    private val activity: Activity,
    private val pdf: PDF,
    private val pref: Preferences,
    private val databaseManager: DatabaseManager,
) {

    data class LoadState(
        val overrideDirection: ReadingDirection?,
        val detectedDirection: ReadingDirection?,
        val effectiveDirection: ReadingDirection,
    )

    suspend fun resolve(fileHash: String?, documentUri: Uri?): LoadState {
        val overrideDirection = fileHash
            ?.let { databaseManager.findReadingDirectionOverride(it) }
            ?.let { ReadingDirection.fromOverrideId(it) }
        val storedDetectedDirection = fileHash
            ?.let { databaseManager.findDetectedReadingDirection(it) }
            ?.let { ReadingDirection.fromId(it) }
        if (overrideDirection != null) {
            return LoadState(
                overrideDirection,
                detectedDirection = storedDetectedDirection,
                effectiveDirection = overrideDirection,
            )
        }

        val detectedDirection = storedDetectedDirection ?: detectIfNeeded(documentUri)

        return LoadState(
            overrideDirection,
            detectedDirection,
            ReadingDirection.effective(overrideDirection, detectedDirection),
        )
    }

    suspend fun detectIfNeeded(documentUri: Uri?): ReadingDirection? {
        if (!pref.getHorizontalScroll() || documentUri == null) {
            return null
        }
        val result = ReadingDirectionDetector.detect(activity, documentUri, pdf.password)
        return result.direction.takeIf { result.cacheable }
    }

    suspend fun saveState(fileHash: String) {
        databaseManager.setReadingDirectionOverride(fileHash, pdf.readingDirectionOverride?.id)
        pdf.detectedReadingDirection?.let {
            databaseManager.setDetectedReadingDirection(fileHash, it.id)
        }
    }
}
