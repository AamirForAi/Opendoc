package com.gitlab.mudlej.MjPdfReader.manager.extractor

import android.util.Log
import com.gitlab.mudlej.MjPdfReader.data.Bookmark
import com.gitlab.mudlej.MjPdfReader.data.Link
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore

private const val TAG = "PdfExtractorImpl"

class PdfExtractorImpl(
    private val pdfiumCore: PdfiumCore,
    private val pdfDocument: PdfDocument
) : PdfExtractor {

    override fun getPageText(pageNumber: Int): String {
        return try {
            getPageTextOrThrow(pageNumber)
        }
        catch (throwable: Throwable) {
            Log.e(TAG, "getPageText: failed for page $pageNumber", throwable)
            ""
        }
    }

    override fun getPageTextOrThrow(pageNumber: Int): String {
        val index = getIndex(pageNumber) ?: return ""
        var opened = false

        pdfiumCore.openPage(pdfDocument, index)
        opened = true
        return try {
            pdfiumCore.getPageText(pdfDocument, index)
        }
        finally {
            if (opened) {
                pdfiumCore.closePage(pdfDocument, index)
            }
        }
    }

    override fun getPageCount() = pdfiumCore.getPageCount(pdfDocument)

    override fun getPageLinks(pageNumber: Int): List<PdfDocument.Link> {
        var opened = false
        try {
            pdfiumCore.openPage(pdfDocument, pageNumber)
            opened = true
        }
        catch (throwable: Throwable) {
            Log.e(TAG, "getPageLinks: failed to open page $pageNumber", throwable)
            return listOf()
        }
        return try {
            pdfiumCore.getPageLinks(pdfDocument, pageNumber).filter { it.uri != null }
        }
        finally {
            if (opened) {
                pdfiumCore.closePage(pdfDocument, pageNumber)
            }
        }
    }

    override fun getAllBookmarks(): List<Bookmark> {
        val tableOfContents = pdfiumCore.getTableOfContents(pdfDocument)
        return tableOfContents.mapIndexed { index, bookmark -> Bookmark(bookmark, level = 0, path = index.toString()) }
    }

    override fun getAllLinks(): List<Link> {
        val links = mutableListOf<Link>()
        for (i in 0 until getPageCount()) {
            val pageLinks = getPageLinks(i)
            for (link in pageLinks) {
                if (link.uri.isNullOrEmpty() || link.uri.isBlank()) {
                    continue
                }
                links.add(Link(
                    text = "",      // couldn't be extracted yet
                    url = link.uri,
                    pageNumber = i + 1
                ))
            }
        }
        return links
    }

    override fun close() {
        try {
            pdfiumCore.closeDocument(pdfDocument)
        } catch (throwable: Throwable) {
            Log.e(TAG, "close: failed to close document", throwable)
        }
    }

    private fun getIndex(pageNumber: Int): Int? {
        return if (pageNumber < 1) null else pageNumber - 1
    }
}
