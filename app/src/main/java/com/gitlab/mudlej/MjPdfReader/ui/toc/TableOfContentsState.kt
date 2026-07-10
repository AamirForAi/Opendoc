package com.gitlab.mudlej.MjPdfReader.ui.toc

import android.content.Intent
import android.os.Bundle
import com.gitlab.mudlej.MjPdfReader.data.PDF

data class TableOfContentsState(
    val expandedPaths: ArrayList<String> = arrayListOf(),
    val scrollPosition: Int = 0,
    val scrollOffset: Int = 0,
    val query: String? = null,
) {
    fun putInto(intent: Intent) {
        intent.putStringArrayListExtra(PDF.bookmarkExpandedPathsKey, ArrayList(expandedPaths))
        intent.putExtra(PDF.bookmarkScrollPositionKey, scrollPosition)
        intent.putExtra(PDF.bookmarkScrollOffsetKey, scrollOffset)
        query?.let { intent.putExtra(PDF.bookmarkQueryKey, it) }
    }

    fun putInto(bundle: Bundle) {
        bundle.putStringArrayList(PDF.bookmarkExpandedPathsKey, ArrayList(expandedPaths))
        bundle.putInt(PDF.bookmarkScrollPositionKey, scrollPosition)
        bundle.putInt(PDF.bookmarkScrollOffsetKey, scrollOffset)
        query?.let { bundle.putString(PDF.bookmarkQueryKey, it) }
    }

    companion object {
        fun from(intent: Intent?): TableOfContentsState {
            if (intent == null) return TableOfContentsState()

            return TableOfContentsState(
                expandedPaths = intent.getStringArrayListExtra(PDF.bookmarkExpandedPathsKey) ?: arrayListOf(),
                scrollPosition = intent.getIntExtra(PDF.bookmarkScrollPositionKey, 0),
                scrollOffset = intent.getIntExtra(PDF.bookmarkScrollOffsetKey, 0),
                query = intent.getStringExtra(PDF.bookmarkQueryKey),
            )
        }

        fun from(bundle: Bundle?): TableOfContentsState {
            if (bundle == null) return TableOfContentsState()

            return TableOfContentsState(
                expandedPaths = bundle.getStringArrayList(PDF.bookmarkExpandedPathsKey) ?: arrayListOf(),
                scrollPosition = bundle.getInt(PDF.bookmarkScrollPositionKey, 0),
                scrollOffset = bundle.getInt(PDF.bookmarkScrollOffsetKey, 0),
                query = bundle.getString(PDF.bookmarkQueryKey),
            )
        }
    }
}
