package com.gitlab.mudlej.MjPdfReader.repository

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ScannedPdfCache(
    @PrimaryKey val path: String,
    val size: Long,
    val lastModified: Long,
    val hash: String?,
)
