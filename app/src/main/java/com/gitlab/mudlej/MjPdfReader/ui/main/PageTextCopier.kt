package com.gitlab.mudlej.MjPdfReader.ui.main

import android.util.Log
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.ui.showCopyPageTextDialog
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageTextCopier(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val pdf: PDF,
    private val scope: CoroutineScope,
) {

    private val shouldStopExtracting: MutableMap<Int, Boolean> = mutableMapOf()
    private var showNoTextInPage = true

    fun resetForNewDocument() {
        shouldStopExtracting.clear()
        showNoTextInPage = true
    }

    fun copyPageText() {
        val pageNumber = pdf.pageNumber
        if (shouldStopExtracting.getOrElse(pageNumber) { false }) {
            return
        }

        var pageText = ""
        scope.launch(Dispatchers.IO) {
            try {
                pageText = binding.pdfView.getPageText(pageNumber)
            }
            catch (e: Throwable) {
                Log.e("PDFium", "extractPageText($pageNumber): error while extracting text", e)
                showFailedExtractTextSnackbar(pageNumber)
            }

            withContext(Dispatchers.Main) {
                if (pageText.isEmpty() || pageText.isBlank()) {
                    showNoTextInPageMessage()
                }
                else {
                    showCopyPageTextDialog(activity, binding, pageNumber, pageText)
                }
            }
        }
    }

    private fun showFailedExtractTextSnackbar(pageNumber: Int) {
        Snackbar.make(binding.root, "Failed to extract text of this file.", Snackbar.LENGTH_SHORT)
            .setAction("Stop this message") { shouldStopExtracting[pageNumber] = true }
            .show()
    }

    private fun showNoTextInPageMessage() {
        if (showNoTextInPage) {
            Snackbar.make(binding.root, "Couldn't find text in this page.", Snackbar.LENGTH_LONG).show()
            showNoTextInPage = false
        }
    }
}
