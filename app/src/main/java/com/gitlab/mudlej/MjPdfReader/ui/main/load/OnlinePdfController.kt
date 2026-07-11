package com.gitlab.mudlej.MjPdfReader.ui.main.load

import android.content.DialogInterface
import android.net.Uri
import android.os.Environment
import android.text.InputType
import android.util.Log
import android.view.View
import androidx.core.net.toUri
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.DocumentState
import com.gitlab.mudlej.MjPdfReader.data.PdfBytesHolder
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.ui.main.MainActivity
import com.gitlab.mudlej.MjPdfReader.util.AppSnackbar
import com.gitlab.mudlej.MjPdfReader.util.canWriteToDownloadFolder
import com.gitlab.mudlej.MjPdfReader.util.readBytesToEnd
import com.gitlab.mudlej.MjPdfReader.util.writeBytesToFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnlinePdfController(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val pdf: DocumentState,
    private val scope: CoroutineScope,
    private val requestSaveToDownloadPermission: () -> Unit,
    private val loadFromBytes: (ByteArray?) -> Unit,
    private val openConfirmedLink: (Uri) -> Unit,
) {

    private sealed class DownloadResult {
        class Success(val bytes: ByteArray?) : DownloadResult()
        data object HttpError : DownloadResult()
        data object SslError : DownloadResult()
        data object GenericError : DownloadResult()
    }

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
        val held = PdfBytesHolder.snapshot()
        return RetainedPdfBytes(held?.uri, held?.bytes)
    }

    fun downloadOrShowDownloadedFile(uri: Uri, retainedState: Any?) {
        if (PdfBytesHolder.snapshot() == null) {
            val retained = retainedState as? RetainedPdfBytes
            if (retained?.uri == uri.toString()) {
                PdfBytesHolder.set(retained.uri, retained.bytes)
            }
        }
        val bytes = PdfBytesHolder.bytesFor(uri.toString())
        if (bytes != null) {
            loadFromBytes(bytes)
        }
        else {
            PdfBytesHolder.clear()
            startDownload(uri.toString())
        }
    }

    private fun startDownload(url: String) {
        binding.progressBar.isIndeterminate = true
        binding.progressBar.progress = 0
        binding.progressBar.visibility = View.VISIBLE
        scope.launch {
            val result = withContext(Dispatchers.IO) { download(url) }
            if (!activity.isDisplayingUri(url)) {
                return@launch
            }
            when (result) {
                is DownloadResult.Success -> {
                    val bytes = result.bytes
                    if (bytes != null) {
                        saveToFileAndDisplay(bytes)
                    } else {
                        showDownloadError(R.string.toast_generic_download_error)
                    }
                }
                is DownloadResult.HttpError -> showDownloadError(R.string.toast_http_code_error)
                is DownloadResult.SslError -> showDownloadError(R.string.toast_ssl_error)
                is DownloadResult.GenericError -> showDownloadError(R.string.toast_generic_download_error)
            }
        }
    }

    private fun download(url: String): DownloadResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                DownloadResult.Success(readBytesToEnd(connection.inputStream))
            } else {
                Log.e(TAG, "Error during http request, response code : $responseCode")
                DownloadResult.HttpError
            }
        } catch (e: SSLException) {
            Log.e(TAG, "Error cannot get file at URL : $url", e)
            DownloadResult.SslError
        } catch (e: IOException) {
            Log.e(TAG, "Error cannot get file at URL : $url", e)
            DownloadResult.GenericError
        } finally {
            connection?.disconnect()
        }
    }

    private fun showDownloadError(messageRes: Int) {
        activity.hideProgress()
        AppSnackbar.make(binding.root, messageRes, Snackbar.LENGTH_LONG).show()
    }

    fun saveToFileAndDisplay(pdfFileContent: ByteArray?) {
        PdfBytesHolder.set(pdf.uri?.toString(), pdfFileContent)
        saveToDownloadFolderIfAllowed(pdfFileContent)
        loadFromBytes(pdfFileContent)
    }

    fun saveDownloadedFileAfterPermissionRequest(isPermissionGranted: Boolean) {
        if (isPermissionGranted) {
            val bytes = PdfBytesHolder.bytesFor(pdf.uri?.toString())
            if (bytes != null) {
                trySaveToDownloads(bytes, true)
            } else {
                AppSnackbar.make(binding.root, R.string.save_to_download_failed, Snackbar.LENGTH_SHORT).show()
            }
        }
        else {
            AppSnackbar.make(binding.root, R.string.save_to_download_failed, Snackbar.LENGTH_SHORT).show()
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
                AppSnackbar.make(binding.root, R.string.saved_to_download, Snackbar.LENGTH_SHORT).show()
            }
        }
        catch (e: IOException) {
            Log.e(TAG, activity.getString(R.string.save_to_download_failed), e)
            AppSnackbar.make(binding.root, R.string.save_to_download_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val TAG = "OnlinePdfController"
    }
}
