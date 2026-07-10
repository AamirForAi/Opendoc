package com.gitlab.mudlej.MjPdfReader.ui.search

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.preference.PreferenceManager
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.SearchResult
import com.gitlab.mudlej.MjPdfReader.databinding.ActivitySearchBinding
import com.gitlab.mudlej.MjPdfReader.manager.extractor.PdfExtractor
import com.gitlab.mudlej.MjPdfReader.util.ColorUtil
import com.gitlab.mudlej.MjPdfReader.util.accentInsensitiveRanges
import com.gitlab.mudlej.MjPdfReader.util.configureSearchIcon
import com.gitlab.mudlej.MjPdfReader.util.containsAccentInsensitive
import com.gitlab.mudlej.MjPdfReader.util.createPdfExtractor
import com.gitlab.mudlej.MjPdfReader.util.indexesOf
import com.gitlab.mudlej.MjPdfReader.util.tintIconsForChrome
import com.gitlab.mudlej.MjPdfReader.util.AppSnackbar
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureTimeMillis

class SearchActivity : AppCompatActivity(), SearchResultFunctions {

    private val tag = javaClass.name
    private lateinit var binding: ActivitySearchBinding
    private val searchResultAdapter = SearchResultAdapter(this)
    private var searchResults: MutableList<SearchResult> = mutableListOf()
    private lateinit var pdfExtractor: PdfExtractor
    private lateinit var actionBarMenu: Menu
    private var fileHash: String? = null
    private var searchQuery: String = ""
    private var restoredListPosition: Int = -1
    private var restoredListOffsetPx: Int = 0
    private var restoredNestedQuery: String? = null
    private var restoredNestedQueryApplied = false
    private var nestedQueryJob: Job? = null
    private var pendingResultClick: SearchResult? = null
    private var ignoreAccents = false

    private val lastPageLiveData = MutableLiveData<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ColorUtil.colorize(this, window, supportActionBar)

        val pref = Preferences(PreferenceManager.getDefaultSharedPreferences(this))
        ignoreAccents = pref.getSearchIgnoreAccents()
        searchResultAdapter.ignoreAccents = ignoreAccents

        lifecycleScope.launch {
            initPdfExtractor()
            if (::pdfExtractor.isInitialized) {
                initUi()
                initSearchResults()
            }
            else {
                finish()
            }
        }
    }

    override fun onPause() {
        saveSearchSessionState()
        super.onPause()
    }

    private fun initUi() {
        initActionBar()
        initLoadingProgressBar()
        initRecyclerView()
    }

    private fun initLoadingProgressBar() {
        binding.searchProgressBar.max = pdfExtractor.getPageCount()
        binding.searchProgressBar.progress = 0
        binding.searchProgressBar.visibility = View.GONE
        hideProgressBar()
        lastPageLiveData.observe(this@SearchActivity) { pageNumber ->
            binding.searchProgressBar.progress = pageNumber
        }
    }

    private fun showProgressBar() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        binding.progressBar.visibility = View.GONE
    }

    private fun initActionBar() {
        // add back button to the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        title = getString(R.string.searching)
    }

    private suspend fun initPdfExtractor() {
        val pdfPath = intent.getStringExtra(PDF.filePathKey)
        val pdfPassword = intent.getStringExtra(PDF.passwordKey)
        try {
            pdfExtractor = withContext(Dispatchers.IO) {
                createPdfExtractor(this@SearchActivity, Uri.parse(pdfPath), pdfPassword)
            }
        }
        catch (throwable: Throwable) {
            Toast.makeText(
                this,
                "Failed to read text! (file move or deleted?)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroy() {
        if (::pdfExtractor.isInitialized) {
            val extractor = pdfExtractor
            thread { runCatching { extractor.close() } }
        }
        super.onDestroy()
    }

    private fun initRecyclerView() {
        searchResultAdapter.submitList(emptyList())
        searchResultAdapter.progressBar = binding.progressBar

        binding.searchRecyclerView.apply {
            adapter = searchResultAdapter
            layoutManager = LinearLayoutManager(this@SearchActivity)
        }
    }

    private fun restorePositionInList() {
        val position = if (restoredListPosition != -1) {
            restoredListPosition
        } else {
            intent.getIntExtra(PDF.resultPositionInListKey, -1)
        }
        if (position == -1) return

        if (position in 0 until searchResultAdapter.itemCount) {
            Log.d(SearchActivity::class.simpleName, "restorePositionInList: $position")
            val layoutManager = binding.searchRecyclerView.layoutManager as? LinearLayoutManager ?: return
            layoutManager.scrollToPositionWithOffset(position, restoredListOffsetPx)
        } else {
            Log.e(
                SearchActivity::class.simpleName,
                "restorePositionInList error: attempted to scroll to invalid position $position in RecyclerView"
            )
        }
    }

    private fun initSearchResults() {
        searchQuery = intent.getStringExtra(PDF.searchQueryKey)?.trim().orEmpty()
        fileHash = intent.getStringExtra(PDF.fileHashKey)
        if (searchQuery.isBlank()) {
            return
        }
        val cachedSession = SearchSessionCache.get(fileHash, searchQuery, ignoreAccents)
        if (cachedSession != null) {
            restoredListPosition = cachedSession.listPosition
            restoredListOffsetPx = cachedSession.listOffsetPx
            restoredNestedQuery = cachedSession.nestedQuery
            showProgressBar()
            binding.searchProgressBar.visibility = View.GONE
        } else {
            hideProgressBar()
            binding.searchProgressBar.progress = 0
            binding.searchProgressBar.visibility = View.VISIBLE
        }

        lifecycleScope.launch(Dispatchers.Default) {
            val results = cachedSession?.let { cachedSearchResults(searchQuery, it.hits) }
                ?: search(searchQuery).also { results ->
                    SearchSessionCache.put(fileHash, searchQuery, ignoreAccents, cacheHits(results))
                }
            withContext(Dispatchers.Main) {
                searchResults = results
                searchResultAdapter.nestedQuery = restoredNestedQuery
                searchResultAdapter.submitList(visibleResultRows())
                hideProgressBar()
                binding.searchProgressBar.visibility = View.GONE
                postSearch()
                if (!restoredNestedQuery.isNullOrBlank()) {
                    invalidateOptionsMenu()
                }
            }
        }
        return
    }

    private suspend fun search(query: String): MutableList<SearchResult> {
        val results = mutableListOf<SearchResult>()
        var lastSubmittedCount = 0
        val pageCount = pdfExtractor.getPageCount()
        val time = measureTimeMillis {
            for (pageNumber in 1 .. pageCount) {
                yield()
                val pageText = pdfExtractor.getPageText(pageNumber)
                if (pageText.isNotBlank()) {
                    matchRanges(pageText, query).forEach { range ->
                        results.add(
                            getPageResult(query, range.first, pageText, pageNumber, matchLength = range.last + 1 - range.first).apply {
                                searchResultIndexInList = results.size
                            }
                        )
                    }
                }
                lastPageLiveData.postValue(pageNumber)

                val isBatchBoundary = pageNumber % SEARCH_BATCH_PAGES == 0 || pageNumber == pageCount
                if (isBatchBoundary && results.size > lastSubmittedCount) {
                    lastSubmittedCount = results.size
                    val snapshot = results.toMutableList()
                    withContext(Dispatchers.Main) {
                        searchResults = snapshot
                        searchResultAdapter.submitList(visibleResultRows())
                        title = "${"%,d".format(snapshot.size)} ${getString(R.string.search_results)}"
                    }
                }
            }
        }
        Log.d(tag, "getSearchResults: elapsed time: ${time / 1000F}s")
        return results
    }

    private fun matchRanges(text: String, query: String): List<IntRange> {
        return if (ignoreAccents) {
            text.accentInsensitiveRanges(query)
        } else {
            text.indexesOf(query, ignoreCase = true).map { it until it + query.length }
        }
    }

    private fun cachedSearchResults(query: String, hits: List<SearchSessionCache.Hit>): MutableList<SearchResult> {
        val pageTextCache = mutableMapOf<Int, String>()
        val results = hits.sortedBy { it.resultIndex }.mapNotNull { hit ->
            val pageText = pageTextCache.getOrPut(hit.pageNumber) { pdfExtractor.getPageText(hit.pageNumber) }
            val matchLength = if (hit.matchLength > 0) hit.matchLength else query.length
            if (hit.originalIndex !in pageText.indices || hit.originalIndex + matchLength > pageText.length) {
                return@mapNotNull null
            }
            getPageResult(
                query,
                hit.originalIndex,
                pageText,
                hit.pageNumber,
                textOffset = if (hit.expanded) 200 else null,
                expanded = hit.expanded,
                matchLength = matchLength,
            ).apply {
                searchResultIndexInList = hit.resultIndex
            }
        }.toMutableList()

        if (results.size != hits.size) {
            results.forEachIndexed { index, result -> result.searchResultIndexInList = index }
            SearchSessionCache.put(fileHash, query, ignoreAccents, cacheHits(results))
        }
        return results
    }

    private fun cacheHits(results: List<SearchResult>): List<SearchSessionCache.Hit> {
        return results.map { result ->
            SearchSessionCache.Hit(
                pageNumber = result.pageNumber,
                originalIndex = result.originalIndex,
                resultIndex = result.searchResultIndexInList,
                expanded = result.expanded,
                matchLength = result.inputEnd - result.inputStart,
            )
        }
    }

    private fun visibleSearchResults(): List<SearchResult> {
        val query = searchResultAdapter.nestedQuery
        return if (query.isNullOrBlank()) {
            searchResults.toList()
        } else if (ignoreAccents) {
            searchResults.filter { it.text.containsAccentInsensitive(query) }
        } else {
            searchResults.filter { it.text.contains(query, true) }
        }
    }

    private fun visibleResultRows(): List<SearchResultRow> {
        val query = searchResultAdapter.nestedQuery
        return visibleSearchResults().map { SearchResultRow(it, query) }
    }

    private fun getPageResult(
        query: String,
        indexInPage: Int,
        pageText: String,
        pageNumber: Int,
        textOffset: Int? = null,
        expanded: Boolean = false,
        matchLength: Int = query.length,
    ): SearchResult {

        val offset = textOffset ?: PDF.SEARCH_RESULT_OFFSET
        val count = matchLength

        val starting = max(0, indexInPage - offset)
        val ending = min(pageText.length, indexInPage + count + offset)
        val resultText = pageText.substring(startIndex = starting, endIndex = ending)

        // remove half words (e.g. "er can I found hi" -> "can I found")
        val queryIndex = indexInPage - starting
        val firstSpace = resultText.indexOf(" ") + 1
        val start = if (firstSpace != -1) min(firstSpace, queryIndex) else 0

        val lastSpace = resultText.lastIndexOf(" ")
        val end = if (lastSpace != -1) max(lastSpace, queryIndex + count) else resultText.length

        val trimmedText = resultText.substring(start, end)
        val newStart = queryIndex - start

        return SearchResult(
            originalIndex = indexInPage,
            inputStart = newStart,
            inputEnd = newStart + count,
            text = trimmedText,
            pageNumber = pageNumber,
            expanded = expanded
        )
    }

    private fun postSearch() {
        if (::actionBarMenu.isInitialized) {
            configureSearchIcon(actionBarMenu, searchResults.isNotEmpty())
        }
        // set up the title in the App Bar
        title = "${"%,d".format(searchResults.size)} ${getString(R.string.search_results)}"

        // show too many results message
        if (searchResults.size > PDF.TOO_MANY_RESULTS) {
            AppSnackbar.make(binding.root,getString(R.string.too_many_results_may_be_slow), Snackbar.LENGTH_INDEFINITE).also {
                it.setAction(getText(R.string.ok)) { }
                it.show()
            }
        }

        // restore if not the first time
        Handler(Looper.getMainLooper()).postDelayed({ restorePositionInList() }, 100)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_menu, menu)
        menu.tintIconsForChrome(this)
        actionBarMenu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // set search functionality
        val searchView = menu.findItem(R.id.search_in_search_activity).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String) = false

            override fun onQueryTextChange(query: String): Boolean {
                nestedQueryJob?.cancel()
                nestedQueryJob = lifecycleScope.launch {
                    delay(NESTED_QUERY_DEBOUNCE_MS)
                    searchResultAdapter.nestedQuery = query
                    showProgressBar()
                    val rows = withContext(Dispatchers.Default) { visibleResultRows() }
                    searchResultAdapter.submitList(rows)
                    AppSnackbar.make(
                        binding.root,
                        getString(R.string.number_of_filtered_results).format(rows.size),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
                return false
            }
        })
        searchView.setOnCloseListener {
            nestedQueryJob?.cancel()
            searchResultAdapter.nestedQuery = null
            searchResultAdapter.submitList(visibleResultRows())
            true
        }
        val nestedQuery = restoredNestedQuery
        if (!restoredNestedQueryApplied && !nestedQuery.isNullOrBlank()) {
            restoredNestedQueryApplied = true
            menu.findItem(R.id.search_in_search_activity).expandActionView()
            searchView.setQuery(nestedQuery, false)
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onSearchResultClicked(searchResult: SearchResult) {
        pendingResultClick = searchResult
        saveSearchSessionState()
        val resultIntent = Intent()
        resultIntent.putExtra(PDF.searchResultKey, Gson().toJson(searchResult))
        resultIntent.putExtra(PDF.searchQueryResultKey, searchQuery)
        resultIntent.putExtra(PDF.searchIgnoreAccentsKey, ignoreAccents)
        setResult(PDF.SEARCH_RESULT_OK, resultIntent)
        finish()
    }

    override fun onShowMoreResultTextClicked(searchResult: SearchResult) {
        val searchResultIndex = searchResult.searchResultIndexInList
        if (searchResultIndex !in searchResults.indices) {
            return
        }
        val query = searchQuery.ifBlank { searchResult.text.substring(searchResult.inputStart, searchResult.inputEnd) }

        lifecycleScope.launch {
            val pageText = withContext(Dispatchers.Default) {
                pdfExtractor.getPageText(searchResult.pageNumber)
            }
            val newSearchResult = getPageResult(
                query,
                searchResult.originalIndex,
                pageText,
                searchResult.pageNumber,
                200,
                expanded = true,
                matchLength = searchResult.inputEnd - searchResult.inputStart,
            )
            newSearchResult.searchResultIndexInList = searchResultIndex
            if (searchResultIndex !in searchResults.indices) {
                return@launch
            }
            searchResults[searchResultIndex] = newSearchResult
            SearchSessionCache.setExpanded(fileHash, searchQuery, ignoreAccents, searchResultIndex, expanded = true)
            searchResultAdapter.submitList(visibleResultRows())
        }
    }

    private fun saveSearchSessionState() {
        if (!::binding.isInitialized || searchQuery.isBlank()) {
            return
        }

        val clicked = pendingResultClick
        if (clicked != null) {
            SearchSessionCache.updateUiState(
                fileHash,
                searchQuery,
                ignoreAccents,
                clicked.searchResultIndexInList,
                0,
                nestedQuery = null,
            )
            return
        }

        val layoutManager = binding.searchRecyclerView.layoutManager as? LinearLayoutManager ?: return
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position == -1) {
            return
        }
        val firstVisibleView = layoutManager.findViewByPosition(position)
        val offsetPx = firstVisibleView?.top?.minus(binding.searchRecyclerView.paddingTop) ?: 0
        SearchSessionCache.updateUiState(
            fileHash,
            searchQuery,
            ignoreAccents,
            position,
            offsetPx,
            searchResultAdapter.nestedQuery,
        )
    }

    companion object {
        private const val NESTED_QUERY_DEBOUNCE_MS = 200L
        private const val SEARCH_BATCH_PAGES = 20
    }
}
