package com.gitlab.mudlej.MjPdfReader.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.link.DefaultLinkHandler
import com.github.barteksc.pdfviewer.link.LinkHandler
import com.github.barteksc.pdfviewer.model.LinkTapEvent
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.SearchResult
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.ui.bookmark.BookmarkState
import com.gitlab.mudlej.MjPdfReader.ui.bookmark.BookmarksActivity
import com.gitlab.mudlej.MjPdfReader.ui.link.LinksActivity
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchActivity
import com.gitlab.mudlej.MjPdfReader.util.AppSnackbar
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ReaderNavigationController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val pdf: PDF,
    private val updateAppTitle: () -> Unit,
    private val launchBookmarks: (Intent) -> Unit,
    private val launchLinks: (Intent) -> Unit,
    private val launchSearch: (Intent) -> Unit,
) {

    private val searchResultsSnackbar = JumpBackSnackbar(binding.root)
    private val bookmarksSnackbar = JumpBackSnackbar(binding.root)
    private val linkJumpSnackbar = JumpBackSnackbar(binding.root)
    private var activeSearchResultPageNumber: Int? = null
    private var bookmarkState = BookmarkState()

    fun createLinkHandler(): LinkHandler = BackTrackingLinkHandler()

    fun showLinks() {
        Intent(activity, LinksActivity::class.java).also { linksIntent ->
            linksIntent.putExtra(PDF.filePathKey, pdf.uri.toString())
            linksIntent.putExtra(PDF.passwordKey, pdf.password)
            launchLinks(linksIntent)
        }
    }

    fun showBookmarks() {
        Intent(activity, BookmarksActivity::class.java).also { bookmarkIntent ->
            bookmarkIntent.putExtra(PDF.filePathKey, pdf.uri.toString())
            bookmarkIntent.putExtra(PDF.passwordKey, pdf.password)
            bookmarkState.putInto(bookmarkIntent)
            launchBookmarks(bookmarkIntent)
        }
    }

    fun clearActiveSearchResultHighlight() {
        activeSearchResultPageNumber?.let { pageNumber ->
            binding.pdfView.clearSearchResultsHighlight(pageNumber)
            activeSearchResultPageNumber = null
        }
    }

    fun resetSearchResultState() {
        clearActiveSearchResultHighlight()
        searchResultsSnackbar.dismiss()
    }

    fun resetBookmarkState() {
        bookmarksSnackbar.dismiss()
        bookmarkState = BookmarkState()
    }

    fun resetLinkJumpState() {
        linkJumpSnackbar.dismiss()
    }

    fun saveState(outState: Bundle) {
        bookmarkState.putInto(outState)
    }

    fun restoreState(savedState: Bundle) {
        bookmarkState = BookmarkState.from(savedState)
    }

    fun handleBookmarksResult(resultCode: Int, intent: Intent?) {
        saveBookmarkState(intent)
        if (resultCode == PDF.BOOKMARK_RESULT_OK) {
            val pageIndex = intent?.getIntExtra(PDF.chosenBookmarkKey, pdf.pageNumber) ?: return
            binding.pdfView.jumpTo(pageIndex)
            showBookmarkNavigationSnackbar()
        }
    }

    fun handleTextModeResult(resultCode: Int, intent: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val pageIndex = intent?.getIntExtra(PDF.pageNumberKey, pdf.pageNumber) ?: return
            val pageCount = binding.pdfView.pageCount
            val boundedPageIndex = if (pageCount > 0) pageIndex.coerceIn(0, pageCount - 1) else pageIndex.coerceAtLeast(0)
            pdf.pageNumber = boundedPageIndex
            updateAppTitle()
            binding.pdfView.jumpTo(boundedPageIndex)
        }
    }

    fun handleLinksResult(resultCode: Int, intent: Intent?) {
        if (resultCode == PDF.LINK_RESULT_OK) {
            val pageNumber = intent?.getIntExtra(PDF.linkResultKey, pdf.pageNumber) ?: return
            val pageIndex = pageNumber - 1
            binding.pdfView.jumpTo(pageIndex)
        }
    }

    fun handleSearchResult(resultCode: Int, intent: Intent?) {
        if (resultCode == PDF.SEARCH_RESULT_OK) {
            val searchResultJson = intent?.getStringExtra(PDF.searchResultKey) ?: return
            val searchResultType = object : TypeToken<SearchResult>() {}.type
            val searchResult = Gson().fromJson<SearchResult>(searchResultJson, searchResultType)

            clearActiveSearchResultHighlight()
            searchResultsSnackbar.dismiss()

            // highlight the result text
            val textBound = binding.pdfView.createHighlightText(
                searchResult.pageNumber,
                searchResult.originalIndex,
                searchResult.inputEnd - searchResult.inputStart,
                true
            )

            if (textBound.isEmpty()) {
                AppSnackbar.make(binding.root, "Failed to highlight search result", Snackbar.LENGTH_SHORT).show()
            }
            else {
                activeSearchResultPageNumber = searchResult.pageNumber
                // because the user may not see the highlight if it was zoomed in before searching
                binding.pdfView.resetZoomWithAnimation()
                binding.pdfView.reloadPages()   // to show the highlighting
            }

            // show a snackbar with a button that will remove the highlight (it wills still be cached for a bit)
            searchResultsSnackbar.show(
                activity.getString(R.string.results),
                onDone = { clearActiveSearchResultHighlight() },
                dismissOnTap = false,
            ) {
                Intent(activity, SearchActivity::class.java).also { searchIntent ->
                    searchIntent.putExtra(PDF.filePathKey, pdf.uri.toString())
                    searchIntent.putExtra(PDF.passwordKey, pdf.password)
                    pdf.fileHash?.let { searchIntent.putExtra(PDF.fileHashKey, it) }
                    pdf.lastQuery?.let { searchIntent.putExtra(PDF.searchQueryKey, it.trim()) }
                    searchIntent.putExtra(PDF.resultPositionInListKey, searchResult.searchResultIndexInList)
                    launchSearch(searchIntent)
                }
            }

            binding.pdfView.jumpUsingPageNumber(searchResult.pageNumber)
        }
        else if (activeSearchResultPageNumber != null || searchResultsSnackbar.isShowing) {
            clearActiveSearchResultHighlight()
            searchResultsSnackbar.dismiss()
            binding.pdfView.reloadPages()
        }
    }

    private fun saveBookmarkState(intent: Intent?) {
        if (intent == null) return

        bookmarkState = BookmarkState.from(intent)
    }

    private fun showBookmarkNavigationSnackbar() {
        resetSearchResultState()
        bookmarksSnackbar.show(activity.getString(R.string.back_to_table_of_contents)) {
            showBookmarks()
        }
    }

    private fun showLinkJumpBackSnackbar(originPageIndex: Int, originViewState: PDFView.ViewState?) {
        resetSearchResultState()
        bookmarksSnackbar.dismiss()
        linkJumpSnackbar.show(activity.getString(R.string.back_to_page, originPageIndex + 1)) {
            if (!binding.pdfView.applyViewState(originViewState)) {
                binding.pdfView.jumpTo(originPageIndex)
            }
        }
    }

    private inner class BackTrackingLinkHandler : LinkHandler {
        private val defaultLinkHandler = DefaultLinkHandler(binding.pdfView)

        override fun handleLinkEvent(event: LinkTapEvent) {
            val destPageIndex = event.link.destPageIdx
            if (event.link.uri.isNullOrEmpty() && destPageIndex != null) {
                val originPageIndex = binding.pdfView.currentPage
                val originViewState = binding.pdfView.captureViewState()
                binding.pdfView.jumpTo(destPageIndex)
                showLinkJumpBackSnackbar(originPageIndex, originViewState)
            } else {
                defaultLinkHandler.handleLinkEvent(event)
            }
        }
    }
}
