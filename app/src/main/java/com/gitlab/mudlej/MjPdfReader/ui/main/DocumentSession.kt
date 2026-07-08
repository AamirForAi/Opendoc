package com.gitlab.mudlej.MjPdfReader.ui.main

import android.net.Uri
import android.os.Bundle
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.enums.ReadingDirection

class DocumentSession(
    val pdf: PDF,
    private val uriAccepted: (Uri?) -> Boolean,
) {

    private var loadToken = 0L

    var pendingViewState: PDFView.ViewState? = null
    var cropMarginsEnabled = false

    val currentLoadToken: Long
        get() = loadToken

    fun isCurrent(loadToken: Long, uri: Uri?): Boolean {
        return this.loadToken == loadToken && uriAccepted(uri)
    }

    fun beginNewDocument(uri: Uri, cropMarginsDefault: Boolean) {
        loadToken++
        pdf.uri = uri
        pdf.fileHash = null
        pdf.pageNumber = 0
        pdf.zoom = 1F
        pendingViewState = null
        pdf.autoScrollSpeed = null
        pdf.readingDirectionOverride = null
        pdf.detectedReadingDirection = null
        pdf.effectiveReadingDirection = ReadingDirection.LEFT_TO_RIGHT
        cropMarginsEnabled = cropMarginsDefault
    }

    fun saveViewState(outState: Bundle, captured: PDFView.ViewState?): PDFView.ViewState? {
        val viewState = captured ?: pendingViewState ?: return null
        outState.putBoolean(PDF.viewStateSavedKey, true)
        outState.putFloat(PDF.viewStateZoomKey, viewState.zoom)
        outState.putInt(PDF.viewStatePageIndexKey, viewState.pageIndex)
        outState.putBoolean(PDF.viewStateSwipeVerticalKey, viewState.swipeVertical)
        outState.putBoolean(PDF.viewStateHorizontalReadingDirectionRtlKey, viewState.horizontalReadingDirectionRtl)
        outState.putFloat(PDF.viewStateRelativeCrossAxisCenterKey, viewState.relativeCrossAxisCenter)
        outState.putFloat(PDF.viewStatePageCenterOffsetRatioKey, viewState.pageCenterOffsetRatio)
        return viewState
    }

    fun restoreViewState(savedState: Bundle): PDFView.ViewState? {
        if (!savedState.getBoolean(PDF.viewStateSavedKey, false)) {
            return null
        }

        return PDFView.ViewState(
            savedState.getFloat(PDF.viewStateZoomKey, 1f),
            savedState.getInt(PDF.viewStatePageIndexKey, 0),
            savedState.getBoolean(PDF.viewStateSwipeVerticalKey, true),
            savedState.getBoolean(PDF.viewStateHorizontalReadingDirectionRtlKey, false),
            savedState.getFloat(PDF.viewStateRelativeCrossAxisCenterKey, 0.5f),
            savedState.getFloat(PDF.viewStatePageCenterOffsetRatioKey, 0.5f),
        )
    }
}
