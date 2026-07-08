package com.gitlab.mudlej.MjPdfReader.ui.bookmark

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.gitlab.mudlej.MjPdfReader.data.Bookmark
import com.gitlab.mudlej.MjPdfReader.databinding.BookmarkRowItemBinding


class BookmarkAdapter(
    val bookmarkFunctions: BookmarkFunctions,
    val activity: BookmarksActivity
) : ListAdapter<BookmarkRow, BookmarkViewHolder>(BookmarkComparator()) {

    private val expandedBookmarkPaths = mutableSetOf<String>()
    private var roots: List<Bookmark> = emptyList()

    var query: String? = null

    fun submitBookmarks(newRoots: List<Bookmark>, commitCallback: (() -> Unit)? = null) {
        roots = newRoots
        submitList(buildRows(), commitCallback)
    }

    fun refresh(commitCallback: (() -> Unit)? = null) {
        submitList(buildRows(), commitCallback)
    }

    private fun buildRows(): List<BookmarkRow> {
        val rows = mutableListOf<BookmarkRow>()
        roots.filter(::matchesSelfOrDescendant).forEach { addRow(it, rows) }
        return rows
    }

    private fun addRow(bookmark: Bookmark, rows: MutableList<BookmarkRow>) {
        val children = visibleChildren(bookmark)
        val expandable = children.isNotEmpty()
        val expanded = expandable && isExpanded(bookmark)
        rows.add(BookmarkRow(bookmark, expandable, expanded))
        if (expanded) children.forEach { addRow(it, rows) }
    }

    fun onToggleClicked(bookmark: Bookmark) {
        if (isFiltering()) return
        toggleExpanded(bookmark)
        refresh()
    }

    fun expandAll() {
        addExpandablePaths(roots)
        refresh()
    }

    fun collapseAll() {
        expandedBookmarkPaths.clear()
        refresh()
    }

    private fun addExpandablePaths(bookmarks: List<Bookmark>) {
        bookmarks.forEach { bookmark ->
            if (bookmark.hasSubBookmarks()) {
                expandedBookmarkPaths.add(bookmark.path)
                addExpandablePaths(bookmark.subBookmarks)
            }
        }
    }

    fun setExpandedBookmarkPaths(paths: Collection<String>) {
        expandedBookmarkPaths.clear()
        expandedBookmarkPaths.addAll(paths)
    }

    fun getExpandedBookmarkPaths(): ArrayList<String> {
        return ArrayList(expandedBookmarkPaths)
    }

    fun toggleExpanded(bookmark: Bookmark): Boolean {
        if (isFiltering()) return isExpanded(bookmark)

        if (!expandedBookmarkPaths.add(bookmark.path)) {
            expandedBookmarkPaths.remove(bookmark.path)
        }
        return isExpanded(bookmark)
    }

    fun isExpanded(bookmark: Bookmark): Boolean {
        return hasMatchingVisibleChild(bookmark) || expandedBookmarkPaths.contains(bookmark.path)
    }

    fun visibleChildren(bookmark: Bookmark): List<Bookmark> {
        return if (query.isNullOrBlank()) {
            bookmark.subBookmarks
        } else {
            bookmark.subBookmarks.filter(::matchesSelfOrDescendant)
        }
    }

    fun matchesSelfOrDescendant(bookmark: Bookmark): Boolean {
        if (!isFiltering()) return true
        return matchesSelf(bookmark) || bookmark.subBookmarks.any(::matchesSelfOrDescendant)
    }

    fun visibleBookmarkCount(bookmarks: List<Bookmark>): Int {
        return bookmarks.sumOf { bookmark -> 1 + visibleBookmarkCount(visibleChildren(bookmark)) }
    }

    fun isFiltering(): Boolean {
        return !query.isNullOrBlank()
    }

    private fun hasMatchingVisibleChild(bookmark: Bookmark): Boolean {
        return isFiltering() && bookmark.subBookmarks.any(::matchesSelfOrDescendant)
    }

    private fun matchesSelf(bookmark: Bookmark): Boolean {
        val activeQuery = query?.trim() ?: return true
        if (activeQuery.isBlank()) return true

        return bookmark.title.orEmpty().contains(activeQuery, ignoreCase = true)
                || (bookmark.pageIdx + 1).toString().contains(activeQuery)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        return BookmarkViewHolder(
            BookmarkRowItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            this,
        )
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, i: Int) {
        getItem(i)?.let { holder.bind(it) }
    }

}
