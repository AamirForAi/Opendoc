package com.gitlab.mudlej.MjPdfReader.ui.reader.navigation

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.ui.reader.DocumentState
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.pdf.SearchResult
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchActivity
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchCoordinator
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchSessionCache
import com.gitlab.mudlej.MjPdfReader.core.ui.AppSnackbar
import com.gitlab.mudlej.MjPdfReader.core.text.NormalizedTextMapper
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar

class SearchNavigationController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val pdf: DocumentState,
    private val historyManager: ReaderHistoryManager,
    private val launchSearch: (Intent) -> Unit,
) {

    private var hits: List<SearchSessionCache.Hit> = emptyList()
    private var currentPosition = -1
    private var hasFullSession = false
    private var subscribedToActiveSearch = false
    private var query = ""
    private var ignoreAccents = false
    private var activeHighlightPageNumber: Int? = null
    private var snackbar: Snackbar? = null
    private var counterView: TextView? = null
    private var previousButton: ImageButton? = null
    private var nextButton: ImageButton? = null

    private val activeSearchListener = object : SearchCoordinator.Listener {
        override fun onProgress(pagesScanned: Int, pageCount: Int) = Unit

        override fun onResults(results: List<SearchResult>, finished: Boolean) {
            if (!subscribedToActiveSearch) {
                return
            }
            val currentResultIndex = hits.getOrNull(currentPosition)?.resultIndex
            hits = SearchCoordinator.cacheHits(results)
            currentPosition = hits
                .indexOfFirst { it.resultIndex == currentResultIndex }
                .takeIf { it >= 0 }
                ?: currentPosition.coerceIn(0, hits.lastIndex.coerceAtLeast(0))
            if (finished) {
                subscribedToActiveSearch = false
                hasFullSession = hits.isNotEmpty()
            }
            updateControls()
        }
    }

    val isActive: Boolean
        get() = snackbar != null || activeHighlightPageNumber != null

    fun start(searchResult: SearchResult, resultQuery: String?, resultIgnoreAccents: Boolean) {
        query = resultQuery?.trim().takeUnless { it.isNullOrBlank() }
            ?: pdf.lastQuery?.trim().orEmpty()
        ignoreAccents = resultIgnoreAccents
        unsubscribeFromActiveSearch()
        val session = SearchSessionCache.get(pdf.fileHash, query, ignoreAccents)
        hasFullSession = session != null
        hits = session?.hits?.sortedBy { it.resultIndex }
            ?: listOf(
                SearchSessionCache.Hit(
                    pageNumber = searchResult.pageNumber,
                    originalIndex = searchResult.originalIndex,
                    resultIndex = searchResult.searchResultIndexInList,
                    matchLength = searchResult.inputEnd - searchResult.inputStart,
                )
            )
        if (session == null) {
            subscribedToActiveSearch =
                SearchCoordinator.subscribeIfRunning(pdf.fileHash, query, ignoreAccents, activeSearchListener)
        }
        currentPosition = hits
            .indexOfFirst { it.resultIndex == searchResult.searchResultIndexInList }
            .takeIf { it >= 0 }
            ?: 0
        historyManager.recordJump(ReaderHistoryManager.Origin.SEARCH, searchResult.pageNumber - 1)
        showSnackbar()
        showCurrentHit()
    }

    private fun unsubscribeFromActiveSearch() {
        subscribedToActiveSearch = false
        SearchCoordinator.unsubscribe(activeSearchListener)
    }

    fun clearHighlight() {
        activeHighlightPageNumber?.let { pageNumber ->
            binding.pdfView.clearSearchResultsHighlight(pageNumber)
            activeHighlightPageNumber = null
        }
    }

    fun reset() {
        unsubscribeFromActiveSearch()
        clearHighlight()
        dismissSnackbar()
        hits = emptyList()
        currentPosition = -1
        hasFullSession = false
    }

    fun resetAndReload() {
        if (!isActive) {
            return
        }
        reset()
        binding.pdfView.reloadPages()
    }

    private fun dismissSnackbar() {
        snackbar?.dismiss()
        snackbar = null
        counterView = null
        previousButton = null
        nextButton = null
    }

    private fun showSnackbar() {
        if (snackbar != null) {
            return
        }
        val bar = AppSnackbar.make(binding.root, activity.getString(R.string.results), Snackbar.LENGTH_INDEFINITE)
        bar.setAction(activity.getString(R.string.done)) {
            SearchCoordinator.cancel(pdf.fileHash, query, ignoreAccents)
            unsubscribeFromActiveSearch()
            clearHighlight()
        }
        bar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                if (snackbar === transientBottomBar) {
                    unsubscribeFromActiveSearch()
                    snackbar = null
                    counterView = null
                    previousButton = null
                    nextButton = null
                }
            }
        })
        val textView = bar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.paintFlags = textView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        textView.setOnClickListener { openResultsList() }
        val density = activity.resources.displayMetrics.density
        val onSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        val highlight = ColorStateList.valueOf(
            MaterialColors.getColor(binding.root, android.R.attr.colorControlHighlight)
        )

        fun borderlessRipple(cornerSize: Float): RippleDrawable {
            val shape = ShapeAppearanceModel.builder().setAllCornerSizes(cornerSize).build()
            return RippleDrawable(highlight, null, MaterialShapeDrawable(shape))
        }

        (textView.parent as? LinearLayout)?.let { content ->
            val buttonSize = (40 * density).toInt()

            fun navigationButton(
                iconRes: Int,
                descriptionRes: Int,
                marginStartDp: Int,
                marginEndDp: Int,
                onClick: () -> Unit,
            ): ImageButton {
                return ImageButton(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        marginStart = (marginStartDp * density).toInt()
                        marginEnd = (marginEndDp * density).toInt()
                    }
                    setImageResource(iconRes)
                    imageTintList = ColorStateList.valueOf(onSurface)
                    background = borderlessRipple(buttonSize / 2f)
                    contentDescription = activity.getString(descriptionRes)
                    setOnClickListener { onClick() }
                }
            }

            val previous = navigationButton(R.drawable.ic_chevron_left, R.string.previous_search_result, 12, 10, ::showPrevious)
            val counter = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginStart = (4 * density).toInt()
                    marginEnd = (4 * density).toInt()
                }
                minWidth = (48 * density).toInt()
                setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                gravity = Gravity.CENTER
                setTextColor(onSurface)
                textSize = 14f
            }
            val next = navigationButton(R.drawable.ic_chevron_right, R.string.next_search_result, 10, 12, ::showNext)

            val insertIndex = content.indexOfChild(textView) + 1
            content.addView(previous, insertIndex)
            content.addView(counter, insertIndex + 1)
            content.addView(next, insertIndex + 2)
            previousButton = previous
            counterView = counter
            nextButton = next
        }
        snackbar = bar
        bar.show()
    }

    private fun showPrevious() {
        if (currentPosition > 0) {
            currentPosition--
            showCurrentHit()
        }
    }

    private fun showNext() {
        if (currentPosition < hits.lastIndex) {
            currentPosition++
            showCurrentHit()
        }
    }

    private fun showCurrentHit() {
        val hit = hits.getOrNull(currentPosition) ?: return
        clearHighlight()
        val matchLength = if (hit.matchLength > 0) hit.matchLength else query.length
        val rawText = binding.pdfView.getPageRawText(hit.pageNumber)
        val rawRange = NormalizedTextMapper.toRawRange(rawText, hit.originalIndex, matchLength)
        val highlightStart = rawRange?.first ?: hit.originalIndex
        val highlightCount = rawRange?.let { it.last + 1 - it.first } ?: matchLength
        val textBounds = binding.pdfView.createHighlightText(hit.pageNumber, highlightStart, highlightCount, true)
        if (textBounds.isEmpty()) {
            AppSnackbar.make(binding.root, R.string.failed_to_highlight_search_result, Snackbar.LENGTH_SHORT).show()
        }
        else {
            activeHighlightPageNumber = hit.pageNumber
            binding.pdfView.resetZoomWithAnimation()
            binding.pdfView.reloadPages()
        }
        binding.pdfView.jumpUsingPageNumber(hit.pageNumber)
        updateControls()
    }

    private fun updateControls() {
        val controlsVisible = hasFullSession || subscribedToActiveSearch
        val visibility = if (controlsVisible) View.VISIBLE else View.GONE
        previousButton?.visibility = visibility
        counterView?.visibility = visibility
        nextButton?.visibility = visibility
        if (!controlsVisible) {
            return
        }
        counterView?.text = activity.getString(R.string.search_result_counter, currentPosition + 1, hits.size)
        setButtonEnabled(previousButton, currentPosition > 0)
        setButtonEnabled(nextButton, currentPosition < hits.lastIndex)
    }

    private fun setButtonEnabled(button: ImageButton?, enabled: Boolean) {
        button?.isEnabled = enabled
        button?.alpha = if (enabled) 1f else 0.35f
    }

    private fun openResultsList() {
        Intent(activity, SearchActivity::class.java).also { searchIntent ->
            searchIntent.putExtra(PDF.filePathKey, pdf.uri.toString())
            searchIntent.putExtra(PDF.passwordKey, pdf.password)
            pdf.fileHash?.let { searchIntent.putExtra(PDF.fileHashKey, it) }
            if (query.isNotBlank()) {
                searchIntent.putExtra(PDF.searchQueryKey, query)
            }
            hits.getOrNull(currentPosition)?.let { hit ->
                searchIntent.putExtra(PDF.resultPositionInListKey, hit.resultIndex)
            }
            launchSearch(searchIntent)
        }
    }
}
