package com.gitlab.mudlej.MjPdfReader.ui.main

import android.content.DialogInterface
import android.net.Uri
import android.os.Environment
import android.text.InputType
import android.util.Log
import android.view.View
import androidx.core.net.toUri
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.PdfBytesHolder
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.util.DownloadPDFFile
import com.gitlab.mudlej.MjPdfReader.util.canWriteToDownloadFolder
import com.gitlab.mudlej.MjPdfReader.util.writeBytesToFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import java.io.IOException

class OnlinePdfController(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val pdf: PDF,
    private val requestSaveToDownloadPermission: () -> Unit,
    private val loadFromBytes: (ByteArray?) -> Unit,
    private val openConfirmedLink: (Uri) -> Unit,
) {

    data class RetainedPdfBytes(val uri: String?, val bytes: ByteArray?)

    fun showOpenOnlinePdfDialog() {
        val inputLayout = activity.layoutInflater.inflate(R.layout.input_layout, null) as TextInputLayout
        inputLayout.hint = activity.getString(R.string.online_pdf_link)
        inputLayout.setStartIconDrawable(R.drawable.ic_link)
        inputLayout.editText?.apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }

        var confirmedHttpLink: String? = null
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.open_online_pdf)
            .setView(inputLayout)
            .setPositiveButton(R.string.open_online_pdf, null)
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .create()

        dialog.setOnShowListener {
            val openButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            openButton.setOnClickListener {
                val link = inputLayout.editText?.text?.toString()?.trim().orEmpty()
                val uri = link.toUri()
                inputLayout.error = null

                if (!uri.scheme.isOnlinePdfScheme() || uri.host.isNullOrBlank()) {
                    confirmedHttpLink = null
                    openButton.setText(R.string.open_online_pdf)
                    inputLayout.error = activity.getString(R.string.invalid_online_pdf_link)
                    return@setOnClickListener
                }

                if (uri.scheme.equals("http", ignoreCase = true) && confirmedHttpLink != link) {
                    confirmedHttpLink = link
                    openButton.setText(R.string.proceed_anyway)
                    inputLayout.error = activity.getString(R.string.http_online_pdf_warning)
                    return@setOnClickListener
                }

                dialog.dismiss()
                openConfirmedLink(uri)
            }
        }
        dialog.show()
    }

    private fun String?.isOnlinePdfScheme(): Boolean {
        return equals("http", ignoreCase = true) || equals("https", ignoreCase = true)
    }

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
