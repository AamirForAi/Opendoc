// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.pdf

import android.app.Activity
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.gitlab.mudlej.MjPdfReader.core.io.forLog
import com.gitlab.mudlej.MjPdfReader.data.OnlineDocumentStore
import com.shockwave.pdfium.PdfiumCore
import java.io.File
import java.io.IOException

object PdfExtractorFactory {

    @Throws(IOException::class)
    fun create(activity: Activity, uri: Uri, password: String? = null): PdfExtractor {
        val pdfium = PdfiumCore(activity)
        val parcelFileDescriptor = activity.contentResolver.openFileDescriptor(uri, "r")
        val pdfDocument = if (password.isNullOrEmpty()) {
            pdfium.newDocument(parcelFileDescriptor)
        } else {
            pdfium.newDocument(parcelFileDescriptor, password)
        }
        return PdfExtractor(pdfium, pdfDocument)
    }

    @Throws(IOException::class)
    fun create(activity: Activity, file: File, password: String? = null): PdfExtractor {
        val pdfium = PdfiumCore(activity)
        val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfDocument = try {
            if (password.isNullOrEmpty()) {
                pdfium.newDocument(parcelFileDescriptor)
            } else {
                pdfium.newDocument(parcelFileDescriptor, password)
            }
        } catch (throwable: Throwable) {
            runCatching { parcelFileDescriptor.close() }
            throw throwable
        }
        return PdfExtractor(pdfium, pdfDocument)
    }

}
fun createPdfExtractor(activity: Activity, uri: Uri, password: String?): PdfExtractor {
    try {
        return PdfExtractorFactory.create(activity, uri, password)
    } catch (throwable: Throwable) {
        Log.w(activity::class.simpleName, "createPdfExtractor: Failed to create PdfExtractor by URI=${uri.forLog()} !", throwable)
    }
    try {
        Log.d(activity::class.simpleName, "createPdfExtractor: Trying to use OnlineDocumentStore file")
        val heldFile = OnlineDocumentStore.fileFor(activity, uri.toString())
        if (heldFile != null) {
            return PdfExtractorFactory.create(activity, heldFile, password)
        }
        else {
            Log.e(activity::class.simpleName, "createPdfExtractor: OnlineDocumentStore file is null!")
            throw RuntimeException("Failed to createPdfExtractor by URI and by cached file")
        }
    } catch (throwable: Throwable) {
        Log.e(activity::class.simpleName, "createPdfExtractor: Failed to create PdfExtractor by cached file!", throwable)
        throw throwable
    }
}
