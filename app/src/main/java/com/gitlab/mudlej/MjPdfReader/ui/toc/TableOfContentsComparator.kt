package com.gitlab.mudlej.MjPdfReader.ui.toc

import androidx.recyclerview.widget.DiffUtil

class TableOfContentsComparator : DiffUtil.ItemCallback<TableOfContentsRow>() {
    override fun areItemsTheSame(oldItem: TableOfContentsRow, newItem: TableOfContentsRow): Boolean
            = oldItem.bookmark.path == newItem.bookmark.path

    override fun areContentsTheSame(oldItem: TableOfContentsRow, newItem: TableOfContentsRow): Boolean
            = oldItem.bookmark.path == newItem.bookmark.path
            && oldItem.bookmark.level == newItem.bookmark.level
            && oldItem.bookmark.title == newItem.bookmark.title
            && oldItem.bookmark.pageIdx == newItem.bookmark.pageIdx
            && oldItem.expandable == newItem.expandable
            && oldItem.expanded == newItem.expanded
}
