package com.gitlab.mudlej.MjPdfReader.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserBookmarkDao {

    @Query("SELECT * FROM UserBookmark WHERE fileHash = :fileHash ORDER BY pageIndex")
    fun findByHash(fileHash: String): List<UserBookmark>

    @Query("SELECT * FROM UserBookmark")
    fun findAll(): List<UserBookmark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(bookmark: UserBookmark)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(bookmarks: List<UserBookmark>)

    @Query("DELETE FROM UserBookmark WHERE fileHash = :fileHash AND pageIndex = :pageIndex")
    fun delete(fileHash: String, pageIndex: Int)

    @Query("UPDATE UserBookmark SET label = :label WHERE fileHash = :fileHash AND pageIndex = :pageIndex")
    fun updateLabel(fileHash: String, pageIndex: Int, label: String?)
}
