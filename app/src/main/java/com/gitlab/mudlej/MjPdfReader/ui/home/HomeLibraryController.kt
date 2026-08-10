// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomeLibraryController(
    private val pdfRepository: PdfRepository,
    private val pref: Preferences,
) {

    suspend fun loadLibrary(availabilityProbe: AvailabilityProbe, annotatedTitleFormat: String): List<HomeItem> {
        val showPdfTitle = pref.getHomeShowPdfTitle()
        val records = pdfRepository.findAllRecords()
        val items = withContext(Dispatchers.IO) {
            records
                .filter { it.fileName.isNotEmpty() }
                .map { HomeItem.from(it, showPdfTitle, availabilityProbe.availabilityOf(it.uri), annotatedTitleFormat) }
        }
        return sort(items)
    }

    fun sort(items: List<HomeItem>): List<HomeItem> {
        return when (pref.getHomeSort()) {
            HomeSortOrder.LAST_OPENED -> items.sortedWith(
                compareByDescending<HomeItem> { it.lastOpened }
                    .thenBy { it.sortKey }
                    .thenBy { it.uri.toString() }
            )
            HomeSortOrder.NAME -> items.sortedWith(
                compareBy<HomeItem> { it.sortKey }.thenBy { it.uri.toString() }
            )
        }
    }

    fun mergeWithScan(records: List<HomeItem>, entries: List<ScannedPdfEntry>): List<HomeItem> {
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
            .distinctBy { it.uri }
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
            .filter { it.hasBeenOpened }
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
        entries: List<ScannedPdfEntry>,
        query: String,
    ): List<HomeItem> {
        if (query.isBlank()) {
            return records
        }
        val recordMatches = filterByQuery(records, query)
        val scanMatches = mergeWithScan(records, entries)
            .filter { it.isScanOnly && it.title.contains(query, ignoreCase = true) }
        return sort(recordMatches + scanMatches)
    }

    companion object {
        private const val HERO_COUNT = 6
        private const val RECENTS_COUNT = 12
    }
}
