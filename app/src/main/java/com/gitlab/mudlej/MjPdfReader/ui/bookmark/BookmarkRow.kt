package com.gitlab.mudlej.MjPdfReader.ui.bookmark

import com.gitlab.mudlej.MjPdfReader.data.Bookmark

data class BookmarkRow(
    val bookmark: Bookmark,
    val expandable: Boolean,
    val expanded: Boolean,
)
