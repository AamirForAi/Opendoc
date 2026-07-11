package com.gitlab.mudlej.MjPdfReader.manager.extractor

import android.app.Activity
import android.net.Uri
import android.util.Log
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.util.createPdfExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

suspend fun Activity.openPdfExtractorFromIntent(): PdfExtractor? {
    val pdfPath = intent.getStringExtra(PDF.filePathKey)
    val pdfPassword = intent.getStringExtra(PDF.passwordKey)
    return try {
        withContext(Dispatchers.IO) {
            createPdfExtractor(this@openPdfExtractorFromIntent, Uri.parse(pdfPath), pdfPassword)
        }
    } catch (throwable: Throwable) {
        Log.e("PdfExtractorLifecycle", "Failed to open PdfExtractor for URI=$pdfPath", throwable)
        null
    }
}

fun PdfExtractor.closeAsync() {
    thread { runCatching { close() } }
}
