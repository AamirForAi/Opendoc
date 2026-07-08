package com.gitlab.mudlej.MjPdfReader.ui.main

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.print.PrintManager
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.PdfBytesHolder
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.manager.print.PdfDocumentAdapter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PrintController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val pdf: PDF,
    private val scope: CoroutineScope,
) {

    fun printFile() {
        val documentUri = pdf.uri
        val bytes = if (PdfBytesHolder.uri == documentUri?.toString()) PdfBytesHolder.pdfByte else null
        if (bytes == null) {
            printUri(documentUri)
            return
        }
        scope.launch {
            val tempUri = withContext(Dispatchers.IO) {
                runCatching {
                    val tempFile = File(activity.cacheDir, "print_temp.pdf")
                    tempFile.writeBytes(bytes)
                    Uri.fromFile(tempFile)
                }.getOrNull()
            }
            printUri(tempUri ?: documentUri)
        }
    }

    private fun printUri(uri: Uri?) {
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        try {
            printManager.print(
                pdf.name,
                PdfDocumentAdapter(activity, uri), null
            )
        }
        catch (e: Throwable) {
            Snackbar.make(binding.root, "Failed to print. Error message: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }
}
