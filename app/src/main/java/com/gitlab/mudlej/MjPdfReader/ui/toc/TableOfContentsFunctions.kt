package com.gitlab.mudlej.MjPdfReader.ui.toc

import com.gitlab.mudlej.MjPdfReader.data.Bookmark

interface TableOfContentsFunctions {
    fun onBookmarkClicked(bookmark: Bookmark)
}