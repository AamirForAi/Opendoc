// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.navigation

import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.data.HistoryPolicy
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderUi
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import com.gitlab.mudlej.MjPdfReader.core.ui.AppSnackbar
import com.gitlab.mudlej.MjPdfReader.core.ui.confirmDialog
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class UserBookmarkController(
    private val binding: ActivityMainBinding,
    private val vm: ReaderViewModel,
    private val pdfRepository: PdfRepository,
    private val historyPolicy: HistoryPolicy,
    private val scope: CoroutineScope,
    private val ui: ReaderUi,
    private val onBookmarkStateChanged: () -> Unit,
    private val isIncognito: () -> Boolean,
    private val onExitIncognito: () -> Unit,
) {

    private val doc get() = vm.doc

    val isCurrentPageBookmarked: Boolean
        get() = visibleRowPages(doc.pageNumber).any { vm.bookmarkedPages.contains(it) }

    fun onPageDisplayed(pageIndex: Int) {
        ensureLoaded()
        refreshActionState(pageIndex)
    }

    fun reload() {
        vm.bookmarksLoadedForHash = null
        ensureLoaded()
    }

    fun toggleCurrentPageBookmark() {
        if (!ui.checkHasFile()) {
            return
        }
        val hash = doc.fileHash
        if (hash == null) {
            AppSnackbar.make(binding.root, R.string.bookmark_hash_unavailable, Snackbar.LENGTH_SHORT).show()
            return
        }
        val pageIndex = doc.pageNumber
        val rowPages = visibleRowPages(pageIndex)
        val bookmarkedRowPages = rowPages.filter { vm.bookmarkedPages.contains(it) }
        val adding = bookmarkedRowPages.isEmpty()
        if (adding && isIncognito()) {
            val context = binding.root.context
            confirmDialog(
                context,
                R.string.bookmark_incognito_blocked_title,
                context.getString(R.string.bookmark_incognito_blocked_message),
                R.string.incognito_exit,
                onExitIncognito,
            )
            return
        }
        if (adding && !historyPolicy.canRecord()) {
            AppSnackbar.make(binding.root, R.string.history_action_blocked, Snackbar.LENGTH_SHORT).show()
            return
        }
        if (adding) {
            vm.bookmarkedPages.add(pageIndex)
        } else {
            vm.bookmarkedPages.removeAll(bookmarkedRowPages.toSet())
        }
        refreshActionState(pageIndex)
        scope.launch {
            if (adding) {
                pdfRepository.addUserBookmark(UserBookmark(hash, pageIndex))
            } else {
                bookmarkedRowPages.forEach { pdfRepository.removeUserBookmark(hash, it) }
            }
        }
    }

    private fun visibleRowPages(pageIndex: Int): IntRange {
        return binding.pdfView.getRowFirstPage(pageIndex)..binding.pdfView.getRowLastPage(pageIndex)
    }

    private fun ensureLoaded() {
        val hash = doc.fileHash ?: return
        if (vm.bookmarksLoadedForHash == hash) {
            return
        }
        vm.bookmarksLoadedForHash = hash
        scope.launch {
            val pages = pdfRepository.findUserBookmarks(hash).map { it.pageIndex }
            if (doc.fileHash == hash) {
                vm.bookmarkedPages.clear()
                vm.bookmarkedPages.addAll(pages)
                refreshActionState(doc.pageNumber)
            }
        }
    }

    private fun refreshActionState(pageIndex: Int) {
        val bookmarked = visibleRowPages(pageIndex).any { vm.bookmarkedPages.contains(it) }
        if (bookmarked != vm.bookmarkActionState) {
            vm.bookmarkActionState = bookmarked
            onBookmarkStateChanged()
        }
    }
}
