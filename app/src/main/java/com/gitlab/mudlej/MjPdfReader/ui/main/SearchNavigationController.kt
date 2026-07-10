package com.gitlab.mudlej.MjPdfReader.ui.main

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.SearchResult
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchActivity
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchSessionCache
import com.gitlab.mudlej.MjPdfReader.util.AppSnackbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar

class SearchNavigationController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val pdf: PDF,
    private val historyManager: ReaderHistoryManager,
    private val launchSearch: (Intent) -> Unit,
) {

    private var hits: List<SearchSessionCache.Hit> = emptyList()
    private var currentPosition = -1
    private var hasFullSession = false
    private var query = ""
    private var ignoreAccents = false
    private var activeHighlightPageNumber: Int? = null
    private var snackbar: Snackbar? = null
    private var counterView: TextView? = null
    private var previousButton: ImageButton? = null
    private var nextButton: ImageButton? = null

    val isActive: Boolean
        get() = snackbar != null || activeHighlightPageNumber != null

    fun start(searchResult: SearchResult, resultQuery: String?, resultIgnoreAccents: Boolean) {
        query = resultQuery?.trim().takeUnless { it.isNullOrBlank() }
            ?: pdf.lastQuery?.trim().orEmpty()
        ignoreAccents = resultIgnoreAccents
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
        currentPosition = hits
            .indexOfFirst { it.resultIndex == searchResult.searchResultIndexInList }
            .takeIf { it >= 0 }
            ?: 0
        historyManager.recordJump(ReaderHistoryManager.Origin.SEARCH, searchResult.pageNumber - 1)
        showSnackbar()
        showCurrentHit()
    }

    fun clearHighlight() {
        activeHighlightPageNumber?.let { pageNumber ->
            binding.pdfView.clearSearchResultsHighlight(pageNumber)
            activeHighlightPageNumber = null
        }
    }

    fun reset() {
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
            clearHighlight()
        }
        bar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                if (snackbar === transientBottomBar) {
                    snackbar = null
                    counterView = null
                    previousButton = null
                    nextButton = null
                }
            }
        })
        val textView = bar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.setOnClickListener { openResultsList() }
        (textView.parent as? LinearLayout)?.let { content ->
            val onSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
            val density = activity.resources.displayMetrics.density
            val buttonSize = (40 * density).toInt()
            val ripple = TypedValue().also {
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }

            fun navigationButton(iconRes: Int, descriptionRes: Int, onClick: () -> Unit): ImageButton {
                return ImageButton(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    setImageResource(iconRes)
                    imageTintList = ColorStateList.valueOf(onSurface)
                    setBackgroundResource(ripple.resourceId)
                    contentDescription = activity.getString(descriptionRes)
                    setOnClickListener { onClick() }
                }
            }

            val previous = navigationButton(R.drawable.ic_chevron_left, R.string.previous_search_result, ::showPrevious)
            val counter = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                minWidth = (40 * density).toInt()
                gravity = Gravity.CENTER
                setTextColor(onSurface)
                textSize = 14f
            }
            val next = navigationButton(R.drawable.ic_chevron_right, R.string.next_search_result, ::showNext)

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
        val textBounds = binding.pdfView.createHighlightText(hit.pageNumber, hit.originalIndex, matchLength, true)
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
        val visibility = if (hasFullSession) View.VISIBLE else View.GONE
        previousButton?.visibility = visibility
        counterView?.visibility = visibility
        nextButton?.visibility = visibility
        if (!hasFullSession) {
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
