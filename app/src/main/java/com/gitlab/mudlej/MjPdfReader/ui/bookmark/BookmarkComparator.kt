package com.gitlab.mudlej.MjPdfReader.ui.bookmark

import androidx.recyclerview.widget.DiffUtil
import com.gitlab.mudlej.MjPdfReader.data.Bookmark

class BookmarkComparator : DiffUtil.ItemCallback<Bookmark>() {
    override fun areItemsTheSame(oldItem: Bookmark, newItem: Bookmark): Boolean
            = oldItem.path == newItem.path

    override fun areContentsTheSame(oldItem: Bookmark, newItem: Bookmark): Boolean
            = oldItem.level == newItem.level
            && oldItem.path == newItem.path
            && oldItem.title == newItem.title
            && oldItem.pageIdx == newItem.pageIdx
            && oldItem.children == newItem.children
}
