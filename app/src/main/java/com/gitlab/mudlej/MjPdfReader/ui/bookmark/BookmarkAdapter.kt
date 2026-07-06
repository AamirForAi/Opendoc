package com.gitlab.mudlej.MjPdfReader.ui.bookmark

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.get
import androidx.recyclerview.widget.ListAdapter
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Bookmark
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.databinding.BookmarksListItemBinding
import com.google.android.material.card.MaterialCardView


class BookmarkAdapter(
    private val bookmarkFunctions: BookmarkFunctions,
    val activity: BookmarksActivity
) : ListAdapter<Bookmark, BookmarkViewHolder>(BookmarkComparator()) {

    private val expandedBookmarkPaths = mutableSetOf<String>()
    private val rootViewCache = mutableMapOf<String, MaterialCardView>()

    var query: String? = null
        set(value) {
            if (field == value) return
            field = value
            clearViewCache()
        }

    fun clearViewCache() {
        rootViewCache.clear()
    }

    fun rootViewFor(bookmark: Bookmark, parent: ViewGroup): MaterialCardView {
        return rootViewCache.getOrPut(bookmark.path) {
            createSubBookmarkLayout(bookmark, parent)
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

    private fun createSubBookmarkLayout(subBookmark: Bookmark, parent: ViewGroup): MaterialCardView {
        val cardView = LayoutInflater.from(activity)
            .inflate(R.layout.children_bookmark_layout, parent, false) as MaterialCardView

        val subBookmarkLayout = cardView[0] as ConstraintLayout
        val subToggleButton = subBookmarkLayout[0] as ImageView
        val subText = subBookmarkLayout[1] as TextView
        val subPageNumber = subBookmarkLayout[2] as TextView
        val subChildrenLayout = subBookmarkLayout[3] as LinearLayoutCompat

        subText.text = subBookmark.title
        subText.textSize = PDF.BOOKMARK_TEXT_SIZE - subBookmark.level * PDF.BOOKMARK_TEXT_SIZE_DEC

        subPageNumber.text = (subBookmark.pageIdx + 1).toString()
        subPageNumber.textSize = PDF.BOOKMARK_TEXT_SIZE - subBookmark.level * PDF.BOOKMARK_TEXT_SIZE_DEC

        subText.setOnClickListener { bookmarkFunctions.onBookmarkClicked(subBookmark) }
        subPageNumber.setOnClickListener { bookmarkFunctions.onBookmarkClicked(subBookmark) }
        subBookmarkLayout.setOnClickListener { bookmarkFunctions.onBookmarkClicked(subBookmark) }

        val visibleChildren = visibleChildren(subBookmark)
        if (visibleChildren.isNotEmpty()) {
            subChildrenLayout.removeAllViews()
            for (child in visibleChildren) {
                val layout = createSubBookmarkLayout(child, subChildrenLayout)
                subChildrenLayout.addView(layout)
            }

            setExpansionState(subChildrenLayout, subToggleButton, isExpanded(subBookmark))

            if (!isFiltering()) {
                subToggleButton.setOnClickListener {
                    val expanded = toggleExpanded(subBookmark)
                    setExpansionState(subChildrenLayout, subToggleButton, expanded)
                }
            }
        }
        else {
            subToggleButton.setImageResource(R.drawable.ic_bullet_point)
        }
        return cardView
    }

    private fun setExpansionState(
        childrenLayout: LinearLayoutCompat,
        toggleButton: ImageView,
        expanded: Boolean
    ) {
        childrenLayout.visibility = if (expanded) View.VISIBLE else View.GONE
        toggleButton.setImageResource(
            if (expanded) R.drawable.ic_small_arrow_down else R.drawable.ic_small_arrow_right
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        return BookmarkViewHolder(
            BookmarksListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            this,
        )
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, i: Int) {
        getItem(i)?.let { holder.bind(it) }
    }

}
