package com.gitlab.mudlej.MjPdfReader.ui.main

import android.app.Activity
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.RectF
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.HighlightPalette
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.util.copyToClipboard
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.gitlab.mudlej.MjPdfReader.util.AppSnackbar
import com.google.android.material.snackbar.Snackbar
import java.util.UUID

class InlineAnnotationActionController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val clearActiveSearchResultHighlight: () -> Unit,
    private val onAnnotationEdit: (AnnotationEdit) -> Unit,
    private val updateSaveUiPosition: () -> Unit,
    private val isDetectExistingHighlightsEnabled: () -> Boolean,
    private val getHighlightColors: () -> List<Int>,
    private val toggleReaderChrome: () -> Unit,
) {
    private var activeHighlightAnnotation: PDFView.HighlightAnnotation? = null

    fun configure(onSaveClicked: () -> Unit) {
        binding.textSelectionCopyButton.setOnClickListener {
            if (copySelectedText()) {
                dismissCard()
            }
        }
        binding.textSelectionSearchWebButton.setOnClickListener {
            if (searchWebForSelectedText()) {
                dismissCard()
            }
        }
        rebuildHighlightSwatches()
        binding.textSelectionDeleteHighlightButton.setOnClickListener { deleteActiveHighlightAnnotation() }
        binding.saveAnnotationsFab.setOnClickListener { onSaveClicked() }
    }

    private val swatchIds = mutableListOf<Int>()

    fun rebuildHighlightSwatches() {
        val container = binding.textSelectionActionContent
        val density = container.resources.displayMetrics.density
        swatchIds.forEach { id -> container.findViewById<View>(id)?.let(container::removeView) }
        swatchIds.clear()
        getHighlightColors().forEach { color ->
            val swatchButton = FrameLayout(container.context).apply {
                id = View.generateViewId()
                layoutParams = ConstraintLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt())
                val backgroundValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, backgroundValue, true)
                setBackgroundResource(backgroundValue.resourceId)
                isClickable = true
                isFocusable = true
                HighlightPalette.fromColor(color)?.let { contentDescription = context.getString(it.labelRes) }
                setOnClickListener { applyHighlightColor(color) }
            }
            val swatchCard = MaterialCardView(container.context).apply {
                layoutParams = FrameLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt(), Gravity.CENTER)
                radius = 18 * density
                cardElevation = 0f
                setCardBackgroundColor(color)
                strokeColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline)
                strokeWidth = density.toInt().coerceAtLeast(1)
            }
            swatchButton.addView(swatchCard)
            container.addView(swatchButton)
            swatchIds.add(swatchButton.id)
        }
        binding.textSelectionActionFlow.referencedIds = (
            swatchIds + listOf(
                R.id.textSelectionDeleteHighlightButton,
                R.id.textSelectionCopyButton,
                R.id.textSelectionSearchWebButton,
            )
        ).toIntArray()
    }

    fun handleImmediatePdfTap(event: MotionEvent): Boolean {
        val annotation = binding.pdfView.findHighlightAnnotationAt(event.x, event.y) ?: return false
        clearActiveSearchResultHighlight()
        binding.pdfView.clearTextSelection()
        showHighlightAnnotationActions(annotation)
        return true
    }

    fun handleEmptyTap() {
        if (activeHighlightAnnotation != null) {
            hideActions()
        }
        toggleReaderChrome()
    }

    fun showSelectionActions(viewBounds: RectF?) {
        val matchingAnnotation = findAnnotationMatchingSelection()
        if (matchingAnnotation != null) {
            binding.pdfView.clearTextSelection()
            showHighlightAnnotationActions(matchingAnnotation)
            return
        }

        activeHighlightAnnotation = null
        binding.pdfView.clearSelectedHighlightAnnotation()
        binding.textSelectionCopyButton.visibility = View.VISIBLE
        binding.textSelectionSearchWebButton.visibility = View.VISIBLE
        binding.textSelectionDeleteHighlightButton.visibility = View.GONE
        showCard(viewBounds)
    }

    private fun findAnnotationMatchingSelection(): PDFView.HighlightAnnotation? {
        if (!isDetectExistingHighlightsEnabled()) {
            return null
        }
        val request = binding.pdfView.getHighlightRequest() ?: return null
        return binding.pdfView.findHighlightAnnotationMatching(request)
    }

    fun hideActions() {
        activeHighlightAnnotation = null
        binding.pdfView.clearSelectedHighlightAnnotation()
        binding.textSelectionActionCard.visibility = View.GONE
        updateSaveUiPosition()
    }

    fun isCardAtBottom(): Boolean {
        val card = binding.textSelectionActionCard
        if (card.visibility != View.VISIBLE) {
            return false
        }
        val params = card.layoutParams as ConstraintLayout.LayoutParams
        return params.verticalBias >= 0.5f
    }

    private fun showHighlightAnnotationActions(annotation: PDFView.HighlightAnnotation) {
        activeHighlightAnnotation = annotation
        binding.pdfView.setSelectedHighlightAnnotation(annotation)
        val textActionVisibility = if (annotation.contents.isBlank()) View.GONE else View.VISIBLE
        binding.textSelectionCopyButton.visibility = textActionVisibility
        binding.textSelectionSearchWebButton.visibility = textActionVisibility
        binding.textSelectionDeleteHighlightButton.visibility = View.VISIBLE
        showCard(annotation.viewBounds)
    }

    private fun showCard(viewBounds: RectF?) {
        val card = binding.textSelectionActionCard
        val params = card.layoutParams as ConstraintLayout.LayoutParams
        if (params.width != ViewGroup.LayoutParams.WRAP_CONTENT) {
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val selectionNearBottom = viewBounds != null && viewBounds.centerY() > binding.pdfView.height * 0.65f
        params.verticalBias = if (selectionNearBottom) 0f else 1f
        card.layoutParams = params
        card.visibility = View.VISIBLE
        refreshCardRendering()
    }

    private fun dismissCard() {
        if (activeHighlightAnnotation != null) {
            hideActions()
        } else {
            binding.pdfView.clearTextSelection()
        }
    }

    private fun applyHighlightColor(color: Int) {
        val annotation = activeHighlightAnnotation
        if (annotation != null) {
            updateActiveHighlightAnnotationColor(annotation, color)
        } else {
            addInlineHighlight(color)
        }
    }

    private fun addInlineHighlight(color: Int) {
        val request = binding.pdfView.getHighlightRequest()
        val groupKey = UUID.randomUUID().toString()
        if (request == null || !binding.pdfView.addHighlight(request, color, groupKey)) {
            AppSnackbar.make(binding.root, R.string.highlight_failed, Snackbar.LENGTH_SHORT).show()
            return
        }

        binding.pdfView.clearTextSelection()
        refreshCardRendering()
        onAnnotationEdit(
            AnnotationEdit.Add(request.pageIndex, groupKey, request.pdfRects, color, request.selectedText)
        )
    }

    private fun updateActiveHighlightAnnotationColor(annotation: PDFView.HighlightAnnotation, color: Int) {
        val updated = binding.pdfView.setHighlightAnnotationColor(annotation, color)
        if (!updated) {
            AppSnackbar.make(binding.root, R.string.highlight_update_failed, Snackbar.LENGTH_SHORT).show()
            return
        }

        hideActions()
        onAnnotationEdit(AnnotationEdit.Recolor(annotation.pageIndex, annotation.groupKey, color))
    }

    private fun deleteActiveHighlightAnnotation() {
        val annotation = activeHighlightAnnotation ?: return
        val removed = binding.pdfView.removeHighlightAnnotation(annotation)
        if (!removed) {
            AppSnackbar.make(binding.root, R.string.highlight_update_failed, Snackbar.LENGTH_SHORT).show()
            return
        }

        hideActions()
        onAnnotationEdit(AnnotationEdit.Delete(annotation.pageIndex, annotation.groupKey))
    }

    private fun copySelectedText(): Boolean {
        val text = selectedText()
        if (text.isBlank()) {
            return false
        }
        copyToClipboard(activity, activity.getString(R.string.selected_text), text)
        return true
    }

    private fun searchWebForSelectedText(): Boolean {
        val text = selectedText()
        if (text.isBlank()) {
            return false
        }
        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text)
        try {
            activity.startActivity(searchIntent)
        } catch (e: ActivityNotFoundException) {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(text)}")))
            } catch (browserError: ActivityNotFoundException) {
                AppSnackbar.make(binding.root, activity.getString(R.string.no_app_to_open_link), Snackbar.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    private fun selectedText(): String {
        return activeHighlightAnnotation?.contents ?: binding.pdfView.getSelectedText()
    }

    private fun refreshCardRendering() {
        val card = binding.textSelectionActionCard
        card.requestLayout()
        card.invalidate()
        binding.root.invalidate()
        card.post {
            card.requestLayout()
            card.invalidate()
            binding.root.invalidate()
            updateSaveUiPosition()
        }
    }
}
