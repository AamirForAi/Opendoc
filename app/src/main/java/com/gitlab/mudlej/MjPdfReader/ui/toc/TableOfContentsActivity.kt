package com.gitlab.mudlej.MjPdfReader.ui.toc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Bookmark
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityTableOfContentsBinding
import com.gitlab.mudlej.MjPdfReader.manager.extractor.PdfExtractor
import com.gitlab.mudlej.MjPdfReader.manager.extractor.closeAsync
import com.gitlab.mudlej.MjPdfReader.manager.extractor.openPdfExtractorFromIntent
import com.gitlab.mudlej.MjPdfReader.util.ColorUtil
import com.gitlab.mudlej.MjPdfReader.util.configureSearchIcon
import com.gitlab.mudlej.MjPdfReader.util.tintIconsForChrome
import com.gitlab.mudlej.MjPdfReader.util.AppSnackbar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TableOfContentsActivity : AppCompatActivity(), TableOfContentsFunctions {
    private lateinit var binding: ActivityTableOfContentsBinding
    private lateinit var pdfExtractor: PdfExtractor
    private val bookmarkAdapter = TableOfContentsAdapter(this, this)
    private var bookmarks: List<Bookmark> = listOf()
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var actionBarMenu: Menu
    private var restoredTableOfContentsState = TableOfContentsState()
    private var activeQuery: String? = null
    private var resultPrepared = false
    private var applyingSearchState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTableOfContentsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ColorUtil.colorize(this, window, supportActionBar)
        restoreTableOfContentsState(savedInstanceState)

        showProgressBar()
        lifecycleScope.launch {
            initPdfExtractor()
            if (::pdfExtractor.isInitialized) {
                initActionBar()
                initUi()
                initBookmarks()
            } else {
                finish()
            }
        }
    }

    private fun restoreTableOfContentsState(savedInstanceState: Bundle?) {
        restoredTableOfContentsState = savedInstanceState?.let { TableOfContentsState.from(it) } ?: TableOfContentsState.from(intent)
        bookmarkAdapter.setExpandedBookmarkPaths(restoredTableOfContentsState.expandedPaths)
        activeQuery = restoredTableOfContentsState.query
        bookmarkAdapter.query = activeQuery
    }

    private fun showProgressBar() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private suspend fun initPdfExtractor() {
        val extractor = openPdfExtractorFromIntent()
        if (extractor == null) {
            Toast.makeText(
                this,
                "Failed to read bookmarks! (file move or deleted?)",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        pdfExtractor = extractor
    }

    override fun onDestroy() {
        if (::pdfExtractor.isInitialized) {
            pdfExtractor.closeAsync()
        }
        super.onDestroy()
    }

    private fun initBookmarks() {
        lifecycleScope.launch {
            val loadedBookmarks = withContext(Dispatchers.Default) {
                pdfExtractor.getAllBookmarks()
            }

            bookmarks = loadedBookmarks
            binding.progressBar.visibility = View.GONE
            submitVisibleBookmarks(restoreScroll = true)
            postGettingBookmarks()
        }
    }

    private fun postGettingBookmarks() {
        if (bookmarks.isNotEmpty()) {
            binding.message.visibility = View.GONE
        }
        else {
            binding.message.text = getString(R.string.no_table_of_contents)
        }

        if (::actionBarMenu.isInitialized) {
            configureSearchIcon(actionBarMenu, bookmarks.isNotEmpty())
            configureExpandCollapseItems(actionBarMenu)
            restoreSearchViewState(actionBarMenu)
        }
    }

    private fun configureExpandCollapseItems(menu: Menu) {
        val show = bookmarks.any { it.hasSubBookmarks() }
        menu.findItem(R.id.expand_all_bookmarks)?.isVisible = show
        menu.findItem(R.id.collapse_all_bookmarks)?.isVisible = show
    }

    private fun initActionBar() {
        // add back button to the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        title = getString(R.string.loading)
    }

    private fun initUi() {
        title = getString(R.string.table_of_contents)
        layoutManager = LinearLayoutManager(this@TableOfContentsActivity)
        binding.bookmarksRecyclerView.apply {
            adapter = bookmarkAdapter
            layoutManager = this@TableOfContentsActivity.layoutManager
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toc_menu, menu)
        menu.tintIconsForChrome(this)
        actionBarMenu = menu
        configureSearchIcon(menu, bookmarks.isNotEmpty())
        configureExpandCollapseItems(menu)
        initSearchView(menu)
        restoreSearchViewState(menu)
        return true
    }

    private fun initSearchView(menu: Menu) {
        val searchItem = menu.findItem(R.id.search_in_search_activity)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String) = false

            override fun onQueryTextChange(query: String): Boolean {
                if (applyingSearchState) return false

                activeQuery = query.trim().takeUnless { it.isBlank() }
                bookmarkAdapter.query = activeQuery
                val visibleBookmarks = submitVisibleBookmarks()
                if (!activeQuery.isNullOrBlank()) {
                    AppSnackbar.make(
                        binding.root,
                        getString(R.string.number_of_filtered_results).format(bookmarkAdapter.visibleBookmarkCount(visibleBookmarks)),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
                return false
            }
        })
        searchView.setOnCloseListener {
            activeQuery = null
            bookmarkAdapter.query = null
            submitVisibleBookmarks()
            false
        }
    }

    private fun restoreSearchViewState(menu: Menu) {
        val query = activeQuery ?: return
        if (query.isBlank()) return

        val searchItem = menu.findItem(R.id.search_in_search_activity)
        if (!searchItem.isVisible) return

        val searchView = searchItem.actionView as SearchView
        applyingSearchState = true
        searchItem.expandActionView()
        searchView.setQuery(query, false)
        searchView.clearFocus()
        applyingSearchState = false
    }

    private fun visibleBookmarks(): List<Bookmark> {
        return if (activeQuery.isNullOrBlank()) {
            bookmarks
        }
        else {
            bookmarks.filter(bookmarkAdapter::matchesSelfOrDescendant)
        }
    }

    private fun submitVisibleBookmarks(restoreScroll: Boolean = false): List<Bookmark> {
        bookmarkAdapter.submitBookmarks(bookmarks) {
            if (restoreScroll) restorePositionInList()
        }
        return visibleBookmarks()
    }

    private fun restorePositionInList() {
        if (!::layoutManager.isInitialized) return
        if (restoredTableOfContentsState.scrollPosition !in 0 until bookmarkAdapter.itemCount) return

        layoutManager.scrollToPositionWithOffset(restoredTableOfContentsState.scrollPosition, restoredTableOfContentsState.scrollOffset)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.expand_all_bookmarks -> bookmarkAdapter.expandAll()
            R.id.collapse_all_bookmarks -> bookmarkAdapter.collapseAll()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onBookmarkClicked(bookmark: Bookmark) {
        setResultWithTableOfContentsState(PDF.BOOKMARK_RESULT_OK, bookmark.pageIdx.toInt())
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        currentTableOfContentsState().putInto(outState)
        super.onSaveInstanceState(outState)
    }

    override fun finish() {
        if (!resultPrepared && ::binding.isInitialized) {
            setResultWithTableOfContentsState(Activity.RESULT_CANCELED)
        }
        super.finish()
    }

    private fun setResultWithTableOfContentsState(resultCode: Int, selectedPageIndex: Int? = null) {
        val resultIntent = Intent()
        currentTableOfContentsState().putInto(resultIntent)
        selectedPageIndex?.let { resultIntent.putExtra(PDF.chosenBookmarkKey, it) }
        resultPrepared = true
        setResult(resultCode, resultIntent)
    }

    private fun currentTableOfContentsState(): TableOfContentsState {
        val (scrollPosition, scrollOffset) = currentScrollState()
        return TableOfContentsState(
            expandedPaths = bookmarkAdapter.getExpandedBookmarkPaths(),
            scrollPosition = scrollPosition,
            scrollOffset = scrollOffset,
            query = activeQuery,
        )
    }

    private fun currentScrollState(): Pair<Int, Int> {
        if (!::layoutManager.isInitialized) {
            return Pair(restoredTableOfContentsState.scrollPosition, restoredTableOfContentsState.scrollOffset)
        }

        val position = layoutManager.findFirstVisibleItemPosition()
        if (position == -1) {
            return Pair(restoredTableOfContentsState.scrollPosition, restoredTableOfContentsState.scrollOffset)
        }

        val view = layoutManager.findViewByPosition(position)
        val offset = view?.top?.minus(binding.bookmarksRecyclerView.paddingTop) ?: restoredTableOfContentsState.scrollOffset
        return Pair(position, offset)
    }

    companion object {
        const val TAG = "TableOfContentsActivity"
    }

}
