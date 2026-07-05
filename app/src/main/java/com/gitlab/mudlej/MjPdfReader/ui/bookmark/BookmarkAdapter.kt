package com.gitlab.mudlej.MjPdfReader.ui.bookmark

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.gitlab.mudlej.MjPdfReader.data.Bookmark
import com.gitlab.mudlej.MjPdfReader.databinding.BookmarksListItemBinding


class BookmarkAdapter(
    private val bookmarkFunctions: BookmarkFunctions,
    val activity: BookmarksActivity
) : ListAdapter<Bookmark, BookmarkViewHolder>(BookmarkComparator()) {

    private val expandedBookmarkPaths = mutableSetOf<String>()
    var query: String? = null

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
            BookmarksListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            bookmarkFunctions,
            this,
            activity
        )
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, i: Int) {
        getItem(i)?.let { holder.bind(it) }
    }

}
