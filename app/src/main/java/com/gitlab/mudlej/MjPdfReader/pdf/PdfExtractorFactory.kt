// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.pdf

import android.app.Activity
import android.net.Uri
import android.util.Log
import com.gitlab.mudlej.MjPdfReader.data.PdfBytesHolder
import com.shockwave.pdfium.PdfiumCore
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
    fun create(activity: Activity, pdfBytes: ByteArray, password: String? = null): PdfExtractor {
        val pdfium = PdfiumCore(activity)
        val pdfDocument =  if (password.isNullOrEmpty()) {
            pdfium.newDocument(pdfBytes)
        } else {
            pdfium.newDocument(pdfBytes, password)
        }
        return PdfExtractor(pdfium, pdfDocument)
    }

}
fun createPdfExtractor(activity: Activity, uri: Uri, password: String?): PdfExtractor {
    try {
        return PdfExtractorFactory.create(activity, uri, password)
    } catch (throwable: Throwable) {
        Log.w(activity::class.simpleName, "createPdfExtractor: Failed to create PdfExtractor by URI=${uri} !", throwable)
    }
    try {
        Log.d(activity::class.simpleName, "createPdfExtractor: Trying to use PdfBytesHolder.pdfByte")
        val heldBytes = PdfBytesHolder.bytesFor(uri.toString())
        if (heldBytes != null) {
            return PdfExtractorFactory.create(activity, heldBytes, password)
        }
        else {
            Log.e(activity::class.simpleName, "createPdfExtractor: PdfBytesHolder.pdfByte is null!", )
            throw RuntimeException("Failed to createPdfExtractor by URI and by PdfBytes")
        }
    } catch (throwable: Throwable) {
        Log.e(activity::class.simpleName, "createPdfExtractor: Failed to create PdfExtractor by PdfBytes!", throwable)
        throw throwable
    }
}
