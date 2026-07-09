package com.gitlab.mudlej.MjPdfReader.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScannedPdfCacheDao {

    @Query("SELECT * FROM ScannedPdfCache")
    fun findAll(): List<ScannedPdfCache>

    @Query("SELECT * FROM ScannedPdfCache WHERE hash = :hash")
    fun findByHash(hash: String): List<ScannedPdfCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entries: List<ScannedPdfCache>)

    @Query("DELETE FROM ScannedPdfCache WHERE path IN (:paths)")
    fun deleteByPaths(paths: List<String>)
}
