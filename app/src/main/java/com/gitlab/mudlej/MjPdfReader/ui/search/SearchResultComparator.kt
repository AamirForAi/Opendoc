package com.gitlab.mudlej.MjPdfReader.ui.search

import androidx.recyclerview.widget.DiffUtil
import com.gitlab.mudlej.MjPdfReader.data.SearchResult

class SearchResultComparator : DiffUtil.ItemCallback<SearchResult>() {
    override fun areItemsTheSame(oldItem: SearchResult, newItem: SearchResult) =
        oldItem.pageNumber == newItem.pageNumber && oldItem.originalIndex == newItem.originalIndex

    override fun areContentsTheSame(oldItem: SearchResult, newItem: SearchResult) =
        oldItem == newItem
}
