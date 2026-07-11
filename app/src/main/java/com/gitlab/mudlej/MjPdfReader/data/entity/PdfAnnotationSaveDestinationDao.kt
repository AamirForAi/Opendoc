package com.gitlab.mudlej.MjPdfReader.data.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PdfAnnotationSaveDestinationDao {
    @Query("SELECT * FROM PdfAnnotationSaveDestination WHERE sourceKey = :sourceKey LIMIT 1")
    fun findBySourceKey(sourceKey: String): PdfAnnotationSaveDestination?

    @Query("SELECT * FROM PdfAnnotationSaveDestination WHERE destinationUri = :destinationUri LIMIT 1")
    fun findByDestinationUri(destinationUri: String): PdfAnnotationSaveDestination?

    @Query("SELECT * FROM PdfAnnotationSaveDestination WHERE lastSavedHash = :hash LIMIT 1")
    fun findByLastSavedHash(hash: String): PdfAnnotationSaveDestination?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(destination: PdfAnnotationSaveDestination)
}
