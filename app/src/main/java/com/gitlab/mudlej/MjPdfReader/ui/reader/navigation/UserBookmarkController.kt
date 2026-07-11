// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.navigation

import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderUi
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import com.gitlab.mudlej.MjPdfReader.core.ui.AppSnackbar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class UserBookmarkController(
    private val binding: ActivityMainBinding,
    private val vm: ReaderViewModel,
    private val pdfRepository: PdfRepository,
    private val scope: CoroutineScope,
    private val ui: ReaderUi,
    private val onBookmarkStateChanged: () -> Unit,
) {

    private val doc get() = vm.doc

    val isCurrentPageBookmarked: Boolean
        get() = vm.bookmarkedPages.contains(doc.pageNumber)

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
        val adding = !vm.bookmarkedPages.contains(pageIndex)
        if (adding) {
            vm.bookmarkedPages.add(pageIndex)
        } else {
            vm.bookmarkedPages.remove(pageIndex)
        }
        refreshActionState(pageIndex)
        scope.launch {
            if (adding) {
                pdfRepository.addUserBookmark(UserBookmark(hash, pageIndex))
            } else {
                pdfRepository.removeUserBookmark(hash, pageIndex)
            }
        }
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
        val bookmarked = vm.bookmarkedPages.contains(pageIndex)
        if (bookmarked != vm.bookmarkActionState) {
            vm.bookmarkActionState = bookmarked
            onBookmarkStateChanged()
        }
    }
}
