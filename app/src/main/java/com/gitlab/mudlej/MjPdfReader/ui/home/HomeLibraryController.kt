package com.gitlab.mudlej.MjPdfReader.ui.home

import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.enums.HomeSortOrder
import com.gitlab.mudlej.MjPdfReader.enums.ListFilter
import com.gitlab.mudlej.MjPdfReader.enums.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManager
import com.gitlab.mudlej.MjPdfReader.repository.ScannedPdfCache

class HomeLibraryController(
    private val databaseManager: DatabaseManager,
    private val pref: Preferences,
) {

    suspend fun loadLibrary(): List<HomeItem> {
        val showPdfTitle = pref.getHomeShowPdfTitle()
        val items = databaseManager.findAllRecords()
            .filter { it.fileName.isNotEmpty() }
            .map { HomeItem.from(it, showPdfTitle) }
        return sort(items)
    }

    fun sort(items: List<HomeItem>): List<HomeItem> {
        return when (pref.getHomeSort()) {
            HomeSortOrder.LAST_OPENED -> items.sortedWith(
                compareByDescending<HomeItem> { it.lastOpened }.thenBy { it.title.lowercase() }
            )
            HomeSortOrder.NAME -> items.sortedBy { it.title.lowercase() }
        }
    }

    fun mergeWithScan(records: List<HomeItem>, entries: List<ScannedPdfCache>): List<HomeItem> {
        val recordsByPath = records
            .filter { it.uri.scheme == "file" }
            .associateBy { it.uri.path }
        val recordsByHash = records.associateBy { it.hash }
        return entries
            .map { entry ->
                val match = recordsByPath[entry.path] ?: entry.hash?.let { recordsByHash[it] }
                match?.copy(
                    sizeBytes = if (match.sizeBytes > 0) match.sizeBytes else entry.size,
                    length = if (match.length > 0) match.length else entry.pageCount,
                ) ?: HomeItem.fromScan(entry)
            }
            .distinctBy { it.hash }
    }

    fun filterByChip(items: List<HomeItem>, filter: ListFilter): List<HomeItem> {
        return when (filter) {
            ListFilter.RECENT -> items
            ListFilter.ALL -> items
            ListFilter.FAVORITE -> items.filter { it.favorite }
            ListFilter.TO_READ -> items.filter { it.readingStatus == ReadingStatus.TO_READ }
            ListFilter.READING -> items.filter { it.readingStatus == ReadingStatus.READING }
            ListFilter.ON_HOLD -> items.filter { it.readingStatus == ReadingStatus.ON_HOLD }
            ListFilter.COMPLETED -> items.filter { it.readingStatus == ReadingStatus.COMPLETED }
            ListFilter.ABANDONED -> items.filter { it.readingStatus == ReadingStatus.ABANDONED }
        }
    }

    fun continueReading(items: List<HomeItem>): List<HomeItem> {
        return items
            .filter { it.hasBeenOpened && it.progressPercent in 1..99 }
            .sortedByDescending { it.lastOpened }
            .take(HERO_COUNT)
    }

    fun recents(items: List<HomeItem>, excluding: List<HomeItem>): List<HomeItem> {
        val excludedHashes = excluding.map { it.hash }.toSet()
        return items
            .filter { it.hasBeenOpened && it.hash !in excludedHashes }
            .sortedByDescending { it.lastOpened }
            .take(RECENTS_COUNT)
    }

    fun filterByQuery(items: List<HomeItem>, query: String): List<HomeItem> {
        if (query.isBlank()) {
            return items
        }
        return items.filter { it.title.contains(query, ignoreCase = true) }
    }

    fun searchAll(
        records: List<HomeItem>,
        entries: List<ScannedPdfCache>,
        query: String,
    ): List<HomeItem> {
        if (query.isBlank()) {
            return records
        }
        val recordMatches = filterByQuery(records, query)
        val scanMatches = mergeWithScan(records, entries)
            .filter { it.isScanOnly && it.title.contains(query, ignoreCase = true) }
        return recordMatches + scanMatches
    }

    companion object {
        private const val HERO_COUNT = 6
        private const val RECENTS_COUNT = 12
    }
}
