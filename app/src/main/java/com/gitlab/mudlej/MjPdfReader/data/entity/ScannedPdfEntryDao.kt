// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScannedPdfEntryDao {

    @Query("SELECT * FROM ScannedPdfCache")
    fun findAll(): List<ScannedPdfEntry>

    @Query("SELECT * FROM ScannedPdfCache WHERE hash = :hash")
    fun findByHash(hash: String): List<ScannedPdfEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entries: List<ScannedPdfEntry>)

    @Query("DELETE FROM ScannedPdfCache WHERE path IN (:paths)")
    fun deleteByPaths(paths: List<String>)

    @Query("UPDATE ScannedPdfCache SET path = :newPath WHERE path = :oldPath")
    fun updatePath(oldPath: String, newPath: String)
}
