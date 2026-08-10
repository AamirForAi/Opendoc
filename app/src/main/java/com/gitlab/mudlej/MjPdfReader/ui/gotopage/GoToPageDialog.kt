// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.gotopage

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.ui.AppSnackbar
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.DialogGoToPageBinding
import com.gitlab.mudlej.MjPdfReader.pdf.PageThumbnailCache
import com.gitlab.mudlej.MjPdfReader.ui.reader.controls.PdfThemeController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

fun showGoToPageDialog(
    activity: ComponentActivity,
    view: View,
    pageIndex: Int,
    pdfLength: Int,
    documentUri: Uri?,
    password: String?,
    goToPageFunc: (Int) -> Unit,
    showAllPages: (() -> Unit)? = null,
) {
    val binding = DialogGoToPageBinding.inflate(LayoutInflater.from(activity))
    binding.inputLayout.hint = "Current page ${pageIndex + 1}/$pdfLength"

    val cache = if (documentUri != null && pdfLength > 0) {
        PageThumbnailCache(activity, documentUri, password)
    } else {
        null
    }

    val builder = MaterialAlertDialogBuilder(activity)
        .setTitle(activity.getString(R.string.go_to_page))
        .setView(binding.root)
        .setPositiveButton(activity.getString(R.string.go_to)) { dialog, _ ->
            val query = binding.editText.text.toString().lowercase().trim()
            if (query.isEmpty()) {
                AppSnackbar.make(view, activity.getString(R.string.no_input), Snackbar.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            query.toIntOrNull()?.let { pageNumber ->
                goToPageFunc(pageNumber - 1)
            }
            dialog.dismiss()
        }
        .setNegativeButton(activity.getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }

    if (cache != null && showAllPages != null) {
        builder.setNeutralButton(activity.getString(R.string.all_pages)) { dialog, _ ->
            dialog.dismiss()
            showAllPages()
        }
    }

    val dialog = builder.create()
    val destroyObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            dialog.dismiss()
        }
    }
    dialog.setOnDismissListener {
        cache?.close()
        activity.lifecycle.removeObserver(destroyObserver)
    }
    activity.lifecycle.addObserver(destroyObserver)

    if (cache != null) {
        val strip = binding.thumbnailStrip
        val pref = Preferences(PreferenceManager.getDefaultSharedPreferences(activity))
        val pdfDarkTheme = PdfThemeController.effectivePdfDarkTheme(activity, pref)
        strip.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        strip.adapter = PageThumbnailStripAdapter(pdfLength, pageIndex, cache, pdfDarkTheme) { chosenIndex ->
            goToPageFunc(chosenIndex)
            dialog.dismiss()
        }
        strip.post { centerStripOn(strip, pageIndex, pdfLength) }
        binding.editText.doAfterTextChanged { text ->
            text?.toString()?.toIntOrNull()
                ?.takeIf { it in 1..pdfLength }
                ?.let { centerStripOn(strip, it - 1, pdfLength) }
        }
    } else {
        binding.thumbnailStrip.visibility = View.GONE
    }

    dialog.show()
}

private fun centerStripOn(strip: RecyclerView, pageIndex: Int, pdfLength: Int) {
    val layoutManager = strip.layoutManager as? LinearLayoutManager ?: return
    val cellWidthPx = (STRIP_CELL_TOTAL_WIDTH_DP * strip.resources.displayMetrics.density).toInt()
    val offset = strip.width / 2 - strip.paddingStart - cellWidthPx / 2
    layoutManager.scrollToPositionWithOffset(pageIndex.coerceIn(0, pdfLength - 1), offset)
}

private const val STRIP_CELL_TOTAL_WIDTH_DP = 98
