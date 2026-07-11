package com.gitlab.mudlej.MjPdfReader.data

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.enums.ReadingDirection

class DocumentState(
    var uri: Uri? = null,
    var name: String = "",
    var password: String? = null,
    var pageNumber: Int = 0,
    var length: Int = 0,
    var autoScrollSpeed: Int? = null,
    var fileHash: String? = null,
    var readingDirectionOverride: ReadingDirection? = null,
    var detectedReadingDirection: ReadingDirection? = null,
    var effectiveReadingDirection: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
    var lastQuery: String? = null,
) {

    fun getTitle(): String {
        val extensionIndex: Int = if (name.lastIndexOf('.') == -1) name.length else name.lastIndexOf('.')
        return name.substring(0, extensionIndex)
    }

    fun getPageCounterText(): String {
        return String.format("[%s/%s]", pageNumber + 1, length)
    }

    fun hasFile() = uri != null

    fun resetLength() {
        length = PDF.RESET_NUMBER
    }

    fun initPdfLength(pageCount: Int) {
        if (length == PDF.RESET_NUMBER) {
            length = pageCount
        }
    }
}
