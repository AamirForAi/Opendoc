package com.gitlab.mudlej.MjPdfReader.ui.search

import com.gitlab.mudlej.MjPdfReader.data.SearchResult

data class SearchResultRow(
    val result: SearchResult,
    val nestedQuery: String?,
)
