package com.gitlab.mudlej.MjPdfReader.data

object PdfBytesHolder {
    var pdfByte: ByteArray? = null
    var uri: String? = null

    fun set(uri: String?, bytes: ByteArray?) {
        this.uri = uri
        this.pdfByte = bytes
    }

    fun clear() {
        pdfByte = null
        uri = null
    }
}
