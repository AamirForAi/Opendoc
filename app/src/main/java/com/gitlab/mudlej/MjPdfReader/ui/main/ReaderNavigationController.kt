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
import com.gitlab.mudlej.MjPdfReader.ui.bookmark.UserBookmarksActivity
import com.gitlab.mudlej.MjPdfReader.ui.history.NavigationHistoryActivity
import com.gitlab.mudlej.MjPdfReader.ui.link.LinksActivity
import com.gitlab.mudlej.MjPdfReader.ui.toc.TableOfContentsActivity
import com.gitlab.mudlej.MjPdfReader.ui.toc.TableOfContentsState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ReaderNavigationController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val pdf: PDF,
    private val historyManager: ReaderHistoryManager,
    private val onPageDisplayed: (Int) -> Unit,
    private val updateAppTitle: () -> Unit,
    private val launchTableOfContents: (Intent) -> Unit,
    private val launchUserBookmarks: (Intent) -> Unit,
    private val launchNavigationHistory: (Intent) -> Unit,
    private val launchLinks: (Intent) -> Unit,
    private val launchSearch: (Intent) -> Unit,
) {

    private val searchNavigationController =
        SearchNavigationController(activity, binding, pdf, historyManager, launchSearch)
    private val tableOfContentsSnackbar = JumpBackSnackbar(binding.root)
    private val linkJumpSnackbar = JumpBackSnackbar(binding.root)
    private var tableOfContentsState = TableOfContentsState()

    fun createLinkHandler(): LinkHandler = BackTrackingLinkHandler()

    fun showLinks() {
        Intent(activity, LinksActivity::class.java).also { linksIntent ->
            linksIntent.putExtra(PDF.filePathKey, pdf.uri.toString())
            linksIntent.putExtra(PDF.passwordKey, pdf.password)
            launchLinks(linksIntent)
        }
    }

    fun showTableOfContents() {
        Intent(activity, TableOfContentsActivity::class.java).also { tocIntent ->
            tocIntent.putExtra(PDF.filePathKey, pdf.uri.toString())
            tocIntent.putExtra(PDF.passwordKey, pdf.password)
            tableOfContentsState.putInto(tocIntent)
            launchTableOfContents(tocIntent)
        }
    }

    fun showUserBookmarks() {
        Intent(activity, UserBookmarksActivity::class.java).also { bookmarksIntent ->
            pdf.fileHash?.let { bookmarksIntent.putExtra(PDF.fileHashKey, it) }
            launchUserBookmarks(bookmarksIntent)
        }
    }

    fun showNavigationHistory() {
        val entries = historyManager.backEntries()
        if (entries.isEmpty()) {
            return
        }
        launchNavigationHistory(NavigationHistoryActivity.createIntent(activity, entries))
    }

    fun onPageChanged(pageIndex: Int) {
        historyManager.onPageChanged(pageIndex)
        onPageDisplayed(pageIndex)
    }

    fun onFileHashComputed() {
        onPageDisplayed(pdf.pageNumber)
    }

    fun clearActiveSearchResultHighlight() {
        searchNavigationController.clearHighlight()
    }

    fun resetSearchResultState() {
        searchNavigationController.reset()
    }

    fun resetTableOfContentsState() {
        tableOfContentsSnackbar.dismiss()
        tableOfContentsState = TableOfContentsState()
    }

    fun resetLinkJumpState() {
        linkJumpSnackbar.dismiss()
    }

    fun saveState(outState: Bundle) {
        tableOfContentsState.putInto(outState)
    }

    fun restoreState(savedState: Bundle) {
        tableOfContentsState = TableOfContentsState.from(savedState)
    }

    fun handleTableOfContentsResult(resultCode: Int, intent: Intent?) {
        saveTableOfContentsState(intent)
        if (resultCode == PDF.BOOKMARK_RESULT_OK) {
            val pageIndex = intent?.getIntExtra(PDF.chosenBookmarkKey, pdf.pageNumber) ?: return
            historyManager.recordJump(ReaderHistoryManager.Origin.TOC, pageIndex)
            binding.pdfView.jumpTo(pageIndex)
            showTableOfContentsJumpBackSnackbar()
        }
    }

    fun handleUserBookmarksResult(resultCode: Int, intent: Intent?) {
        if (resultCode == PDF.BOOKMARK_RESULT_OK) {
            val pageIndex = intent?.getIntExtra(PDF.chosenBookmarkKey, pdf.pageNumber) ?: return
            historyManager.recordJump(ReaderHistoryManager.Origin.BOOKMARK, pageIndex)
            binding.pdfView.jumpTo(pageIndex)
        }
    }

    fun handleNavigationHistoryResult(resultCode: Int, intent: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val backStackIndex = intent?.getIntExtra(NavigationHistoryActivity.EXTRA_SELECTED_BACK_STACK_INDEX, -1) ?: return
            historyManager.goBackToBackStackIndex(backStackIndex)
        }
    }

    fun handleTextModeResult(resultCode: Int, intent: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val pageIndex = intent?.getIntExtra(PDF.pageNumberKey, pdf.pageNumber) ?: return
            val pageCount = binding.pdfView.pageCount
            val boundedPageIndex = if (pageCount > 0) pageIndex.coerceIn(0, pageCount - 1) else pageIndex.coerceAtLeast(0)
            historyManager.recordJump(ReaderHistoryManager.Origin.TEXT_MODE, boundedPageIndex)
            pdf.pageNumber = boundedPageIndex
            updateAppTitle()
            binding.pdfView.jumpTo(boundedPageIndex)
        }
    }

    fun handleLinksResult(resultCode: Int, intent: Intent?) {
        if (resultCode == PDF.LINK_RESULT_OK) {
            val pageNumber = intent?.getIntExtra(PDF.linkResultKey, pdf.pageNumber) ?: return
            val pageIndex = pageNumber - 1
            historyManager.recordJump(ReaderHistoryManager.Origin.LINK, pageIndex)
            binding.pdfView.jumpTo(pageIndex)
        }
    }

    fun handleSearchResult(resultCode: Int, intent: Intent?) {
        if (resultCode == PDF.SEARCH_RESULT_OK) {
            val searchResultJson = intent?.getStringExtra(PDF.searchResultKey) ?: return
            val searchResultType = object : TypeToken<SearchResult>() {}.type
            val searchResult = Gson().fromJson<SearchResult>(searchResultJson, searchResultType)

            searchNavigationController.start(
                searchResult,
                intent.getStringExtra(PDF.searchQueryResultKey),
                intent.getBooleanExtra(PDF.searchIgnoreAccentsKey, false),
            )
        }
        else {
            searchNavigationController.resetAndReload()
        }
    }

    private fun saveTableOfContentsState(intent: Intent?) {
        if (intent == null) return

        tableOfContentsState = TableOfContentsState.from(intent)
    }

    private fun showTableOfContentsJumpBackSnackbar() {
        resetSearchResultState()
        linkJumpSnackbar.dismiss()
        tableOfContentsSnackbar.show(activity.getString(R.string.back_to_table_of_contents)) {
            showTableOfContents()
        }
    }

    private fun showLinkJumpBackSnackbar(originPageIndex: Int, originViewState: PDFView.ViewState?) {
        resetSearchResultState()
        tableOfContentsSnackbar.dismiss()
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
                historyManager.recordJump(ReaderHistoryManager.Origin.LINK, destPageIndex)
                binding.pdfView.jumpTo(destPageIndex)
                showLinkJumpBackSnackbar(originPageIndex, originViewState)
            } else {
                defaultLinkHandler.handleLinkEvent(event)
            }
        }
    }
}
