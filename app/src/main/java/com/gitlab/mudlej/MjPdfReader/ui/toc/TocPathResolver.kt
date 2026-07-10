package com.gitlab.mudlej.MjPdfReader.ui.toc

import android.app.Activity
import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.data.Bookmark
import com.gitlab.mudlej.MjPdfReader.util.createPdfExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TocPathResolver private constructor(
    private val entries: List<Entry>,
) {

    data class Entry(
        val pageIndex: Int,
        val path: String,
    )

    fun resolve(pageIndex: Int): String? {
        if (entries.isEmpty() || pageIndex < 0) {
            return null
        }
        return entries.lastOrNull { it.pageIndex <= pageIndex }?.path
    }

    companion object {
        val EMPTY = TocPathResolver(emptyList())

        private const val PATH_SEPARATOR = " ▶ "

        suspend fun load(activity: Activity, pdfPath: String?, password: String?): TocPathResolver {
            if (pdfPath.isNullOrBlank()) {
                return EMPTY
            }
            return withContext(Dispatchers.IO) {
                try {
                    val extractor = createPdfExtractor(activity, Uri.parse(pdfPath), password)
                    try {
                        fromBookmarks(extractor.getAllBookmarks())
                    } finally {
                        runCatching { extractor.close() }
                    }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    EMPTY
                }
            }
        }

        private fun fromBookmarks(bookmarks: List<Bookmark>): TocPathResolver {
            val paths = mutableListOf<Pair<Int, List<String>>>()
            bookmarks.forEach { bookmark -> addBookmark(paths, bookmark, emptyList()) }
            return TocPathResolver(
                paths
                    .sortedWith(compareBy({ it.first }, { it.second.size }))
                    .map { (pageIndex, titles) -> Entry(pageIndex, titles.joinToString(PATH_SEPARATOR)) }
            )
        }

        private fun addBookmark(paths: MutableList<Pair<Int, List<String>>>, bookmark: Bookmark, parentTitles: List<String>) {
            val title = bookmark.title?.trim().orEmpty()
            val titles = if (title.isBlank()) parentTitles else parentTitles + title
            if (bookmark.pageIdx >= 0 && titles.isNotEmpty()) {
                paths.add(bookmark.pageIdx.toInt() to titles)
            }
            bookmark.subBookmarks.forEach { child -> addBookmark(paths, child, titles) }
        }
    }
}
