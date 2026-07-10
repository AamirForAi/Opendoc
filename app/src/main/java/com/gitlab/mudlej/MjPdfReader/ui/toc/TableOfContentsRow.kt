package com.gitlab.mudlej.MjPdfReader.ui.toc

import com.gitlab.mudlej.MjPdfReader.data.Bookmark

data class TableOfContentsRow(
    val bookmark: Bookmark,
    val expandable: Boolean,
    val expanded: Boolean,
)
