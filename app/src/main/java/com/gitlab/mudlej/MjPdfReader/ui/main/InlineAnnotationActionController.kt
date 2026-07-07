package com.gitlab.mudlej.MjPdfReader.ui.main

import android.app.Activity
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.RectF
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.util.copyToClipboard
import com.google.android.material.snackbar.Snackbar
import java.util.UUID

class InlineAnnotationActionController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val clearActiveSearchResultHighlight: () -> Unit,
    private val onAnnotationEdit: (AnnotationEdit) -> Unit,
    private val updateSaveUiPosition: () -> Unit,
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
        binding.textSelectionHighlightYellowButton.setOnClickListener { applyHighlightColor(HIGHLIGHT_YELLOW) }
        binding.textSelectionHighlightOrangeButton.setOnClickListener { applyHighlightColor(HIGHLIGHT_ORANGE) }
        binding.textSelectionHighlightRedButton.setOnClickListener { applyHighlightColor(HIGHLIGHT_PINK_RED) }
        binding.textSelectionHighlightBlueButton.setOnClickListener { applyHighlightColor(HIGHLIGHT_BLUE) }
        binding.textSelectionHighlightGreenButton.setOnClickListener { applyHighlightColor(HIGHLIGHT_GREEN) }
        binding.textSelectionDeleteHighlightButton.setOnClickListener { deleteActiveHighlightAnnotation() }
        binding.saveAnnotationsFab.setOnClickListener { onSaveClicked() }
    }

    fun handleImmediatePdfTap(event: MotionEvent): Boolean {
        val annotation = binding.pdfView.findHighlightAnnotationAt(event.x, event.y) ?: return false
        clearActiveSearchResultHighlight()
        binding.pdfView.clearTextSelection()
        showHighlightAnnotationActions(annotation)
        return true
    }

    fun handlePdfTap(event: MotionEvent): Boolean {
        if (handleImmediatePdfTap(event)) {
            return true
        }

        if (activeHighlightAnnotation != null) {
            hideActions()
        }
        toggleReaderChrome()
        return true
    }

    fun showSelectionActions(viewBounds: RectF?) {
        activeHighlightAnnotation = null
        binding.pdfView.clearSelectedHighlightAnnotation()
        binding.textSelectionCopyButton.visibility = View.VISIBLE
        binding.textSelectionSearchWebButton.visibility = View.VISIBLE
        binding.textSelectionDeleteHighlightButton.visibility = View.GONE
        showCard(viewBounds)
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
            Snackbar.make(binding.root, R.string.highlight_failed, Snackbar.LENGTH_SHORT).show()
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
            Snackbar.make(binding.root, R.string.highlight_update_failed, Snackbar.LENGTH_SHORT).show()
            return
        }

        hideActions()
        onAnnotationEdit(AnnotationEdit.Recolor(annotation.pageIndex, annotation.groupKey, color))
    }

    private fun deleteActiveHighlightAnnotation() {
        val annotation = activeHighlightAnnotation ?: return
        val removed = binding.pdfView.removeHighlightAnnotation(annotation)
        if (!removed) {
            Snackbar.make(binding.root, R.string.highlight_update_failed, Snackbar.LENGTH_SHORT).show()
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
                Snackbar.make(binding.root, activity.getString(R.string.no_app_to_open_link), Snackbar.LENGTH_LONG).show()
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

    private companion object {
        val HIGHLIGHT_YELLOW = 0xFFFFF176.toInt()
        val HIGHLIGHT_ORANGE = 0xFFFFB74D.toInt()
        val HIGHLIGHT_PINK_RED = 0xFFF06292.toInt()
        val HIGHLIGHT_BLUE = 0xFF64B5F6.toInt()
        val HIGHLIGHT_GREEN = 0xFF81C784.toInt()
    }
}
