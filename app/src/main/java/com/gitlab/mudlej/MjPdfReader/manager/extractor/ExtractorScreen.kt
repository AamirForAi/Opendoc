package com.gitlab.mudlej.MjPdfReader.manager.extractor

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ExtractorScreen(private val activity: AppCompatActivity) {

    var extractor: PdfExtractor? = null
        private set

    fun open(failureMessage: String, onReady: suspend (PdfExtractor) -> Unit) {
        activity.lifecycleScope.launch {
            val opened = activity.openPdfExtractorFromIntent()
            if (opened == null) {
                Toast.makeText(activity, failureMessage, Toast.LENGTH_SHORT).show()
                activity.finish()
                return@launch
            }
            extractor = opened
            onReady(opened)
        }
    }

    fun close() {
        extractor?.closeAsync()
        extractor = null
    }
}
