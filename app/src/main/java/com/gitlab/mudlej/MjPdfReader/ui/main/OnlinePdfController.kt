package com.gitlab.mudlej.MjPdfReader.ui.main

import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.View
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.PdfBytesHolder
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.util.DownloadPDFFile
import com.gitlab.mudlej.MjPdfReader.util.canWriteToDownloadFolder
import com.gitlab.mudlej.MjPdfReader.util.writeBytesToFile
import com.google.android.material.snackbar.Snackbar
import java.io.IOException

class OnlinePdfController(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val pdf: PDF,
    private val requestSaveToDownloadPermission: () -> Unit,
    private val loadFromBytes: (ByteArray?) -> Unit,
) {

    data class RetainedPdfBytes(val uri: String?, val bytes: ByteArray?)

    fun retainSnapshot(): RetainedPdfBytes {
        return RetainedPdfBytes(PdfBytesHolder.uri, PdfBytesHolder.pdfByte)
    }

    fun downloadOrShowDownloadedFile(uri: Uri, retainedState: Any?) {
        if (PdfBytesHolder.pdfByte == null) {
            val retained = retainedState as? RetainedPdfBytes
            if (retained?.uri == uri.toString()) {
                PdfBytesHolder.set(retained.uri, retained.bytes)
            }
        }
        if (PdfBytesHolder.pdfByte != null && PdfBytesHolder.uri != uri.toString()) {
            PdfBytesHolder.clear()
        }
        if (PdfBytesHolder.pdfByte != null) {
            loadFromBytes(PdfBytesHolder.pdfByte)
        }
        else {
            // we will get the pdf asynchronously with the DownloadPDFFile object
            binding.progressBar.isIndeterminate = true
            binding.progressBar.progress = 0
            binding.progressBar.visibility = View.VISIBLE
            val downloadPDFFile = DownloadPDFFile(activity, binding, uri.toString())
            downloadPDFFile.execute(uri.toString())
        }
    }

    fun saveToFileAndDisplay(pdfFileContent: ByteArray?) {
        PdfBytesHolder.set(pdf.uri?.toString(), pdfFileContent)
        saveToDownloadFolderIfAllowed(pdfFileContent)
        loadFromBytes(pdfFileContent)
    }

    fun saveDownloadedFileAfterPermissionRequest(isPermissionGranted: Boolean) {
        if (isPermissionGranted) {
            val bytes = if (PdfBytesHolder.uri == pdf.uri?.toString()) PdfBytesHolder.pdfByte else null
            if (bytes != null) {
                trySaveToDownloads(bytes, true)
            } else {
                Snackbar.make(binding.root, R.string.save_to_download_failed, Snackbar.LENGTH_SHORT).show()
            }
        }
        else {
            Snackbar.make(binding.root, R.string.save_to_download_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun saveToDownloadFolderIfAllowed(fileContent: ByteArray?) {
        if (canWriteToDownloadFolder(activity)) {
            trySaveToDownloads(fileContent, false)
        }
        else {
            requestSaveToDownloadPermission()
        }
    }

    private fun trySaveToDownloads(fileContent: ByteArray?, showSuccessMessage: Boolean) {
        try {
            val downloadDirectory =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            writeBytesToFile(downloadDirectory, pdf.name, fileContent)
            if (showSuccessMessage) {
                Snackbar.make(binding.root, R.string.saved_to_download, Snackbar.LENGTH_SHORT).show()
            }
        }
        catch (e: IOException) {
            Log.e(TAG, activity.getString(R.string.save_to_download_failed), e)
            Snackbar.make(binding.root, R.string.save_to_download_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val TAG = "OnlinePdfController"
    }
}
