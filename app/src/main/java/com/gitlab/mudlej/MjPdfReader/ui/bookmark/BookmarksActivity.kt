package com.gitlab.mudlej.MjPdfReader.ui.bookmark

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityBookmarksBinding
import com.gitlab.mudlej.MjPdfReader.manager.extractor.PdfExtractor
import com.gitlab.mudlej.MjPdfReader.util.ColorUtil
import com.gitlab.mudlej.MjPdfReader.util.configureSearchIcon
import com.gitlab.mudlej.MjPdfReader.util.createPdfExtractor
import com.gitlab.mudlej.MjPdfReader.util.tintIconsForChrome
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookmarksActivity : AppCompatActivity(), BookmarkFunctions {
    private lateinit var binding: ActivityBookmarksBinding
    private lateinit var pdfExtractor: PdfExtractor
    private val bookmarkAdapter = BookmarkAdapter(this, this)
    private var bookmarks: List<Bookmark> = listOf()
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var actionBarMenu: Menu
    private var restoredBookmarkState = BookmarkState()
    private var activeQuery: String? = null
    private var resultPrepared = false
    private var applyingSearchState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookmarksBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ColorUtil.colorize(this, window, supportActionBar)
        restoreBookmarkState(savedInstanceState)

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

    private fun restoreBookmarkState(savedInstanceState: Bundle?) {
        restoredBookmarkState = savedInstanceState?.let { BookmarkState.from(it) } ?: BookmarkState.from(intent)
        bookmarkAdapter.setExpandedBookmarkPaths(restoredBookmarkState.expandedPaths)
        activeQuery = restoredBookmarkState.query
        bookmarkAdapter.query = activeQuery
    }

    private fun showProgressBar() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun initPdfExtractor() {
        val pdfPath = intent.getStringExtra(PDF.filePathKey)
        val pdfPassword = intent.getStringExtra(PDF.passwordKey)
        try {
            pdfExtractor = createPdfExtractor(this, Uri.parse(pdfPath), pdfPassword)
        }
        catch (throwable: Throwable) {
            Toast.makeText(
                this,
                "Failed to read bookmarks! (file move or deleted?)",
                Toast.LENGTH_SHORT
            ).show()
        }
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
            restoreSearchViewState(actionBarMenu)
        }
    }

    private fun initActionBar() {
        // add back button to the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        title = getString(R.string.loading)
    }

    private fun initUi() {
        title = getString(R.string.table_of_contents)
        layoutManager = LinearLayoutManager(this@BookmarksActivity)
        binding.bookmarksRecyclerView.apply {
            adapter = bookmarkAdapter
            layoutManager = this@BookmarksActivity.layoutManager
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_menu, menu)
        menu.tintIconsForChrome(this)
        actionBarMenu = menu
        configureSearchIcon(menu, bookmarks.isNotEmpty())
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
                    Snackbar.make(
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
        if (restoredBookmarkState.scrollPosition !in 0 until bookmarkAdapter.itemCount) return

        layoutManager.scrollToPositionWithOffset(restoredBookmarkState.scrollPosition, restoredBookmarkState.scrollOffset)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onBookmarkClicked(bookmark: Bookmark) {
        setResultWithBookmarkState(PDF.BOOKMARK_RESULT_OK, bookmark.pageIdx.toInt())
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        currentBookmarkState().putInto(outState)
        super.onSaveInstanceState(outState)
    }

    override fun finish() {
        if (!resultPrepared && ::binding.isInitialized) {
            setResultWithBookmarkState(Activity.RESULT_CANCELED)
        }
        super.finish()
    }

    private fun setResultWithBookmarkState(resultCode: Int, selectedPageIndex: Int? = null) {
        val resultIntent = Intent()
        currentBookmarkState().putInto(resultIntent)
        selectedPageIndex?.let { resultIntent.putExtra(PDF.chosenBookmarkKey, it) }
        resultPrepared = true
        setResult(resultCode, resultIntent)
    }

    private fun currentBookmarkState(): BookmarkState {
        val (scrollPosition, scrollOffset) = currentScrollState()
        return BookmarkState(
            expandedPaths = bookmarkAdapter.getExpandedBookmarkPaths(),
            scrollPosition = scrollPosition,
            scrollOffset = scrollOffset,
            query = activeQuery,
        )
    }

    private fun currentScrollState(): Pair<Int, Int> {
        if (!::layoutManager.isInitialized) {
            return Pair(restoredBookmarkState.scrollPosition, restoredBookmarkState.scrollOffset)
        }

        val position = layoutManager.findFirstVisibleItemPosition()
        if (position == -1) {
            return Pair(restoredBookmarkState.scrollPosition, restoredBookmarkState.scrollOffset)
        }

        val view = layoutManager.findViewByPosition(position)
        val offset = view?.top?.minus(binding.bookmarksRecyclerView.paddingTop) ?: restoredBookmarkState.scrollOffset
        return Pair(position, offset)
    }

    companion object {
        const val TAG = "BookmarksActivity"
    }

}
