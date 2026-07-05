package com.gitlab.mudlej.MjPdfReader.ui.text_reader

sealed class TextReaderPageState(open val pageIndex: Int) {
    data class NotLoaded(override val pageIndex: Int) : TextReaderPageState(pageIndex)
    data class Loading(override val pageIndex: Int) : TextReaderPageState(pageIndex)
    data class Ready(override val pageIndex: Int, val text: String) : TextReaderPageState(pageIndex)
    data class Empty(override val pageIndex: Int) : TextReaderPageState(pageIndex)
    data class Error(override val pageIndex: Int, val message: String) : TextReaderPageState(pageIndex)
}
