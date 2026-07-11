package com.gitlab.mudlej.MjPdfReader.ui.text_mode

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.pdf.PdfExtractor
import com.gitlab.mudlej.MjPdfReader.pdf.createPdfExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections

class TextModeContentLoader(
    private val activity: AppCompatActivity,
    private val recyclerView: RecyclerView,
) {

    var pageCount = 0
        private set
    val closing: Boolean
        get() = isClosing

    private lateinit var pdfExtractor: PdfExtractor
    private lateinit var adapter: TextModePageAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var currentPageIndex: () -> Int
    private lateinit var isSeekSettling: () -> Boolean

    private var pendingExtractor: PdfExtractor? = null

    @Volatile
    private var isClosing = false
    private val extractionMutex = Mutex()
    private val extractionJobs = Collections.synchronizedSet(mutableSetOf<Job>())
    private val loadingPages = mutableSetOf<Int>()
    private val textCache = LinkedHashMap<Int, String>(CACHE_PAGE_LIMIT, 0.75f, true)
    private val loadVisiblePagesRunnable = Runnable {
        if (::layoutManager.isInitialized) {
            loadVisiblePages()
        }
    }

    suspend fun open(uri: Uri, password: String?): Boolean {
        val extractor = withContext(Dispatchers.IO) {
            extractionMutex.withLock {
                try {
                    createPdfExtractor(activity, uri, password).also { created ->
                        if (isClosing) {
                            created.close()
                        } else {
                            pendingExtractor = created
                        }
                    }
                } catch (throwable: Throwable) {
                    null
                }
            }
        } ?: return false

        return withContext(Dispatchers.IO) {
            extractionMutex.withLock {
                if (isClosing) {
                    extractor.close()
                    if (pendingExtractor === extractor) {
                        pendingExtractor = null
                    }
                    return@withLock false
                }

                pdfExtractor = extractor
                pendingExtractor = null
                pageCount = pdfExtractor.getPageCount()
                true
            }
        }
    }

    fun attach(
        adapter: TextModePageAdapter,
        layoutManager: LinearLayoutManager,
        currentPageIndex: () -> Int,
        isSeekSettling: () -> Boolean,
    ) {
        this.adapter = adapter
        this.layoutManager = layoutManager
        this.currentPageIndex = currentPageIndex
        this.isSeekSettling = isSeekSettling
    }

    fun loadAround(pageIndex: Int) {
        for (index in pageIndex - PREFETCH_DISTANCE..pageIndex + PREFETCH_DISTANCE) {
            loadPage(index)
        }
    }

    fun loadTargetWindow(pageIndex: Int) {
        for (index in (pageIndex - PREFETCH_DISTANCE)..(pageIndex + JUMP_LOAD_AHEAD)) {
            loadPage(index)
        }
    }

    fun loadVisiblePages() {
        val firstVisiblePage = layoutManager.findFirstVisibleItemPosition()
        val lastVisiblePage = layoutManager.findLastVisibleItemPosition()
        if (firstVisiblePage == RecyclerView.NO_POSITION || lastVisiblePage == RecyclerView.NO_POSITION) {
            loadAround(currentPageIndex())
            return
        }

        for (index in (firstVisiblePage - PREFETCH_DISTANCE)..(lastVisiblePage + PREFETCH_DISTANCE)) {
            loadPage(index)
        }
    }

    fun scheduleLoadVisiblePages() {
        if (isSeekSettling()) return

        recyclerView.removeCallbacks(loadVisiblePagesRunnable)
        recyclerView.post(loadVisiblePagesRunnable)
    }

    fun retryPage(pageIndex: Int) {
        loadPage(pageIndex, force = true)
    }

    fun close() {
        isClosing = true
        recyclerView.removeCallbacks(loadVisiblePagesRunnable)
        synchronized(extractionJobs) {
            extractionJobs.toList()
        }.forEach { it.cancel() }
        CoroutineScope(Dispatchers.IO + NonCancellable).launch {
            extractionMutex.withLock {
                pendingExtractor?.close()
                pendingExtractor = null
                if (::pdfExtractor.isInitialized) {
                    pdfExtractor.close()
                }
            }
        }
    }

    private fun loadPage(pageIndex: Int, force: Boolean = false) {
        if (pageIndex !in 0 until pageCount) return

        textCache[pageIndex]?.let { cachedText ->
            val currentState = adapter.pageState(pageIndex)
            if (currentState !is TextModePageState.Ready || currentState.text != cachedText) {
                updatePageState(TextModePageState.Ready(pageIndex, cachedText))
                scheduleLoadVisiblePages()
            }
            return
        }
        if (loadingPages.contains(pageIndex)) return
        if (!force) {
            when (adapter.pageState(pageIndex)) {
                is TextModePageState.Ready,
                is TextModePageState.Empty,
                is TextModePageState.Error -> return
                else -> Unit
            }
        }

        loadingPages.add(pageIndex)
        updatePageState(TextModePageState.Loading(pageIndex))
        val job = activity.lifecycleScope.launch(Dispatchers.IO) {
            val state = extractionMutex.withLock {
                val relevant = withContext(Dispatchers.Main) { isPageStillWanted(pageIndex) }
                if (!relevant) {
                    null
                } else {
                    try {
                        val rawText = pdfExtractor.getPageTextOrThrow(pageIndex + 1)
                        val text = TextModeTextFormatter.format(rawText)
                        if (text.isBlank()) {
                            TextModePageState.Empty(pageIndex)
                        } else {
                            TextModePageState.Ready(pageIndex, text)
                        }
                    } catch (throwable: Throwable) {
                        TextModePageState.Error(pageIndex, throwable.message.orEmpty())
                    }
                }
            }

            withContext(Dispatchers.Main) {
                loadingPages.remove(pageIndex)
                if (state == null) {
                    if (adapter.pageState(pageIndex) is TextModePageState.Loading) {
                        updatePageState(TextModePageState.NotLoaded(pageIndex))
                    }
                    return@withContext
                }
                if (state is TextModePageState.Ready) {
                    cacheText(state.pageIndex, state.text)
                }
                updatePageState(state)
                scheduleLoadVisiblePages()
            }
        }
        extractionJobs.add(job)
        job.invokeOnCompletion { extractionJobs.remove(job) }
    }

    private fun isPageStillWanted(pageIndex: Int): Boolean {
        if (isClosing || !::layoutManager.isInitialized) return false

        val nearCurrent = pageIndex in (currentPageIndex() - PREFETCH_DISTANCE)..(currentPageIndex() + JUMP_LOAD_AHEAD)
        if (nearCurrent) return true

        val firstVisiblePage = layoutManager.findFirstVisibleItemPosition()
        val lastVisiblePage = layoutManager.findLastVisibleItemPosition()
        if (firstVisiblePage == RecyclerView.NO_POSITION || lastVisiblePage == RecyclerView.NO_POSITION) return true

        return pageIndex in (firstVisiblePage - PREFETCH_DISTANCE)..(lastVisiblePage + PREFETCH_DISTANCE)
    }

    private fun updatePageState(state: TextModePageState) {
        if (recyclerView.isComputingLayout) {
            recyclerView.post { updatePageState(state) }
            return
        }
        if (state is TextModePageState.Loading && !loadingPages.contains(state.pageIndex)) {
            return
        }
        adapter.updatePageState(state)
    }

    private fun cacheText(pageIndex: Int, text: String) {
        textCache[pageIndex] = text
        if (textCache.size <= CACHE_PAGE_LIMIT) return

        val firstVisiblePage = layoutManager.findFirstVisibleItemPosition()
        val lastVisiblePage = layoutManager.findLastVisibleItemPosition()
        val protectedRange = if (firstVisiblePage == RecyclerView.NO_POSITION || lastVisiblePage == RecyclerView.NO_POSITION) {
            (currentPageIndex() - PREFETCH_DISTANCE)..(currentPageIndex() + JUMP_LOAD_AHEAD)
        } else {
            (firstVisiblePage - PREFETCH_DISTANCE)..(lastVisiblePage + PREFETCH_DISTANCE)
        }

        val iterator = textCache.keys.iterator()
        while (textCache.size > CACHE_PAGE_LIMIT && iterator.hasNext()) {
            val eldestPageIndex = iterator.next()
            if (eldestPageIndex == pageIndex || eldestPageIndex in protectedRange) continue
            iterator.remove()
            updatePageState(TextModePageState.NotLoaded(eldestPageIndex))
        }
    }

    private companion object {
        const val PREFETCH_DISTANCE = 2
        const val JUMP_LOAD_AHEAD = 8
        const val CACHE_PAGE_LIMIT = 24
    }
}
