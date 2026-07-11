// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

object PdfBytesHolder {

    class Snapshot(val uri: String?, val bytes: ByteArray)

    @Volatile
    private var snapshot: Snapshot? = null

    fun snapshot(): Snapshot? = snapshot

    fun bytesFor(uri: String?): ByteArray? = snapshot?.takeIf { it.uri == uri }?.bytes

    fun set(uri: String?, bytes: ByteArray?) {
        snapshot = if (bytes == null) null else Snapshot(uri, bytes)
    }

    fun clear() {
        snapshot = null
    }
}
