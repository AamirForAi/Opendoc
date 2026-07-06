package com.gitlab.mudlej.MjPdfReader.ui.bookmark

import androidx.recyclerview.widget.DiffUtil

class BookmarkComparator : DiffUtil.ItemCallback<BookmarkRow>() {
    override fun areItemsTheSame(oldItem: BookmarkRow, newItem: BookmarkRow): Boolean
            = oldItem.bookmark.path == newItem.bookmark.path

    override fun areContentsTheSame(oldItem: BookmarkRow, newItem: BookmarkRow): Boolean
            = oldItem.bookmark.path == newItem.bookmark.path
            && oldItem.bookmark.level == newItem.bookmark.level
            && oldItem.bookmark.title == newItem.bookmark.title
            && oldItem.bookmark.pageIdx == newItem.bookmark.pageIdx
            && oldItem.expandable == newItem.expandable
            && oldItem.expanded == newItem.expanded
}
