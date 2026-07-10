package com.gitlab.mudlej.MjPdfReader.repository

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import java.time.LocalDateTime

@Entity(primaryKeys = ["fileHash", "pageIndex"], indices = [Index("fileHash")])
data class UserBookmark(
    val fileHash: String,
    val pageIndex: Int,
    val label: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @ColumnInfo(defaultValue = "-1") val sortOrder: Int = -1,
)
