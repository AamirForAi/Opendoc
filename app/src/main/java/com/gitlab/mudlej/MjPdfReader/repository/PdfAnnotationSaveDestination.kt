package com.gitlab.mudlej.MjPdfReader.repository

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity
data class PdfAnnotationSaveDestination(
    @PrimaryKey val sourceKey: String,
    val destinationUri: String,
    val lastSavedHash: String?,
    val lastSavedAt: LocalDateTime,
)
