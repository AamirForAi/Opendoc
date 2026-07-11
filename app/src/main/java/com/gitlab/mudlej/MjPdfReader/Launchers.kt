package com.gitlab.mudlej.MjPdfReader

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.gitlab.mudlej.MjPdfReader.data.DocumentState
import com.gitlab.mudlej.MjPdfReader.ui.main.MainActivity
import com.gitlab.mudlej.MjPdfReader.util.PersistedGrantKeeper

fun openSelectedDocument(activity: MainActivity, pdf: DocumentState, selectedDocumentUri: Uri?) {
    if (selectedDocumentUri == null) return

    if (pdf.uri == null || selectedDocumentUri == pdf.uri) {
        try {
            activity.initPdf(pdf, selectedDocumentUri)
            activity.displayFromUri(pdf.uri, true)
        } catch (e: Throwable) {
            Log.e("Launchers", "openSelectedDocument: ", e)
            Toast.makeText(activity, "Failed to open the document!", Toast.LENGTH_LONG).show()
        }
    } else {
        val intent = Intent(activity, activity.javaClass)
        intent.data = selectedDocumentUri
        activity.startActivity(intent)
    }
}

class Launcher(private val activity: MainActivity, private val pdf: DocumentState) {

    fun pdfPicker(): ActivityResultLauncher<Array<String>>
        = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { selectedDocumentUri: Uri? ->
            if (selectedDocumentUri != null) {
                PersistedGrantKeeper.takeReadGrant(activity, selectedDocumentUri)
            }
            openSelectedDocument(activity, pdf, selectedDocumentUri)
    }

    fun saveToDownloadPermission(requestFunction:(Boolean) -> (Unit))
        = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            isPermissionGranted: Boolean -> requestFunction(isPermissionGranted)
        }

    fun readFileErrorPermission(requestFunction:(Boolean) -> (Unit))
        = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            isPermissionGranted: Boolean -> requestFunction(isPermissionGranted)
        }

    fun settings(requestFunction: (Uri?) -> Unit)
        = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            requestFunction(pdf.uri)
        }
}

class Launchers(
    val pdfPicker: ActivityResultLauncher<Array<String>>,
    val saveToDownloadPermission: ActivityResultLauncher<String>,
    val readFileErrorPermission: ActivityResultLauncher<String>,
    val settings: ActivityResultLauncher<Intent>,
)