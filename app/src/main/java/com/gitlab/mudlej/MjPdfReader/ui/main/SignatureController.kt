package com.gitlab.mudlej.MjPdfReader.ui.main

import android.app.Activity
import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit
import com.gitlab.mudlej.MjPdfReader.data.signature.SignatureData
import com.gitlab.mudlej.MjPdfReader.data.signature.SignatureStore
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.databinding.DialogSignatureBinding
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class SignatureController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val store: SignatureStore,
    private val annotationController: AnnotationController,
    private val onAnnotationEdit: (AnnotationEdit) -> Unit,
    private val updateDirtyUi: () -> Unit,
) {

    private var dirtyBeforePlacement = false
    private var restoredPage = -1
    private var restoredRect: RectF? = null

    init {
        binding.cancelSignatureFab.setOnClickListener { cancelPlacement() }
    }

    fun showSignatureDialog() {
        if (binding.pdfView.hasPendingStampPlacement()) {
            Snackbar.make(binding.root, R.string.signature_pending_hint, Snackbar.LENGTH_SHORT).show()
            return
        }
        val sheetBinding = DialogSignatureBinding.inflate(LayoutInflater.from(activity))
        store.load()?.let { sheetBinding.signatureView.setSignature(it) }
        configureColorSwatches(sheetBinding)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.signature_dialog_title)
            .setView(sheetBinding.root)
            .setPositiveButton(R.string.signature_place, null)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.signature_clear, null)
            .show()
        val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        positiveButton.setOnClickListener {
            val data = sheetBinding.signatureView.buildSignatureData() ?: return@setOnClickListener
            store.save(data)
            dialog.dismiss()
            startPlacement(data)
        }
        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            sheetBinding.signatureView.clear()
        }
        positiveButton.isEnabled = sheetBinding.signatureView.hasInk()
        sheetBinding.signatureView.onInkChanged = {
            positiveButton.isEnabled = sheetBinding.signatureView.hasInk()
        }
    }

    private fun configureColorSwatches(sheetBinding: DialogSignatureBinding) {
        val swatches = listOf(
            SignatureColorSwatch(
                sheetBinding.signatureColorBlackButton,
                sheetBinding.signatureColorBlackSwatch,
                SignatureView.DEFAULT_INK_COLOR,
            ),
            SignatureColorSwatch(sheetBinding.signatureColorBlueButton, sheetBinding.signatureColorBlueSwatch, INK_BLUE),
            SignatureColorSwatch(sheetBinding.signatureColorRedButton, sheetBinding.signatureColorRedSwatch, INK_RED),
            SignatureColorSwatch(sheetBinding.signatureColorGreenButton, sheetBinding.signatureColorGreenSwatch, INK_GREEN),
        )
        val selectedStroke = MaterialColors.getColor(
            sheetBinding.root, androidx.appcompat.R.attr.colorPrimary)
        val defaultStroke = MaterialColors.getColor(
            sheetBinding.root, com.google.android.material.R.attr.colorOutline)
        val density = activity.resources.displayMetrics.density
        fun applySelection(selectedColor: Int) {
            for (swatch in swatches) {
                val selected = swatch.color == selectedColor
                swatch.colorView.strokeColor = if (selected) selectedStroke else defaultStroke
                swatch.colorView.strokeWidth = ((if (selected) 3f else 1f) * density).toInt()
            }
        }
        for (swatch in swatches) {
            swatch.hitTarget.setOnClickListener {
                sheetBinding.signatureView.setInkColor(swatch.color)
                applySelection(swatch.color)
            }
        }
        val initialColor = sheetBinding.signatureView.currentInkColor()
        if (swatches.none { it.color == initialColor }) {
            sheetBinding.signatureView.setInkColor(SignatureView.DEFAULT_INK_COLOR)
        }
        applySelection(sheetBinding.signatureView.currentInkColor())
    }

    private data class SignatureColorSwatch(
        val hitTarget: android.view.View,
        val colorView: com.google.android.material.card.MaterialCardView,
        val color: Int,
    )

    fun hasPendingPlacement(): Boolean = binding.pdfView.hasPendingStampPlacement()

    fun commitPendingSignature(): Boolean {
        val pending = binding.pdfView.getPendingStampPlacement() ?: return true
        if (!binding.pdfView.commitPendingStampPlacement()) {
            Snackbar.make(binding.root, R.string.signature_commit_failed, Snackbar.LENGTH_SHORT).show()
            return false
        }
        onAnnotationEdit(
            AnnotationEdit.AddSignature(
                page = pending.pageIndex,
                rect = pending.pdfRect,
                strokes = pending.strokes.toList(),
                color = pending.color,
                strokeWidth = pending.normalizedStrokeWidth,
            )
        )
        hideCancelAffordance()
        return true
    }

    fun cancelPlacement() {
        if (!binding.pdfView.hasPendingStampPlacement()) {
            hideCancelAffordance()
            return
        }
        binding.pdfView.cancelStampPlacement()
        hideCancelAffordance()
        if (!dirtyBeforePlacement) {
            annotationController.clearDirty()
        }
        updateDirtyUi()
    }

    fun saveState(outState: Bundle) {
        val pending = binding.pdfView.getPendingStampPlacement() ?: return
        outState.putBoolean(KEY_PENDING, true)
        outState.putInt(KEY_PAGE, pending.pageIndex)
        outState.putFloatArray(
            KEY_RECT,
            floatArrayOf(pending.pdfRect.left, pending.pdfRect.top, pending.pdfRect.right, pending.pdfRect.bottom),
        )
        outState.putBoolean(KEY_DIRTY_BEFORE, dirtyBeforePlacement)
    }

    fun restoreState(savedState: Bundle) {
        if (!savedState.getBoolean(KEY_PENDING, false)) {
            return
        }
        restoredPage = savedState.getInt(KEY_PAGE, -1)
        val values = savedState.getFloatArray(KEY_RECT)
        if (restoredPage >= 0 && values != null && values.size == 4) {
            restoredRect = RectF(values[0], values[1], values[2], values[3])
        }
        dirtyBeforePlacement = savedState.getBoolean(KEY_DIRTY_BEFORE, false)
    }

    fun resumeRestoredPlacementIfNeeded() {
        val rect = restoredRect ?: return
        val page = restoredPage
        restoredPage = -1
        restoredRect = null
        val data = store.load() ?: return
        binding.pdfView.startStampPlacement(page, rect, data.toNativeStrokes(), data.color, data.strokeWidth)
        annotationController.markDirty()
        showCancelAffordance()
        updateDirtyUi()
    }

    private fun startPlacement(data: SignatureData) {
        dirtyBeforePlacement = annotationController.hasUnsavedAnnotations
        val started = binding.pdfView.startStampPlacementAtViewCenter(
            data.toNativeStrokes(), data.color, data.strokeWidth, data.aspect, PAGE_WIDTH_FRACTION)
        if (!started) {
            Snackbar.make(binding.root, R.string.signature_commit_failed, Snackbar.LENGTH_SHORT).show()
            return
        }
        annotationController.markDirty()
        showCancelAffordance()
        updateDirtyUi()
        Snackbar.make(binding.root, R.string.signature_pending_hint, Snackbar.LENGTH_SHORT).show()
    }

    private fun showCancelAffordance() {
        binding.cancelSignatureFab.visibility = android.view.View.VISIBLE
    }

    private fun hideCancelAffordance() {
        binding.cancelSignatureFab.visibility = android.view.View.GONE
    }

    private companion object {
        const val PAGE_WIDTH_FRACTION = 0.4f
        const val INK_BLUE = 0xFF1E4FC2.toInt()
        const val INK_RED = 0xFFC62828.toInt()
        const val INK_GREEN = 0xFF2E7D32.toInt()
        const val KEY_PENDING = "signaturePlacementPending"
        const val KEY_PAGE = "signaturePlacementPage"
        const val KEY_RECT = "signaturePlacementRect"
        const val KEY_DIRTY_BEFORE = "signaturePlacementDirtyBefore"
    }
}
