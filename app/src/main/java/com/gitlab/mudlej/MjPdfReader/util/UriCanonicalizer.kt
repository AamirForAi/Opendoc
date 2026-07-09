package com.gitlab.mudlej.MjPdfReader.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

object UriCanonicalizer {

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val DOWNLOADS_AUTHORITY = "com.android.providers.downloads.documents"
    private const val PRIMARY_VOLUME = "primary"
    private const val RAW_PREFIX = "raw:"

    fun canonicalize(context: Context, uri: Uri): File? {
        val file = when (uri.scheme) {
            "file" -> uri.path?.let { File(it) }
            "content" -> fromContentUri(context, uri)
            else -> null
        }
        return file?.takeIf { it.exists() && it.canRead() }
    }

    private fun fromContentUri(context: Context, uri: Uri): File? {
        return when (uri.authority) {
            EXTERNAL_STORAGE_AUTHORITY -> fromExternalStorageDocument(uri)
            DOWNLOADS_AUTHORITY -> fromDownloadsDocument(context, uri)
            else -> fromMediaStoreData(context, uri)
        }
    }

    private fun fromExternalStorageDocument(uri: Uri): File? {
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val split = docId.split(":", limit = 2)
        if (split.size != 2) {
            return null
        }
        val (volume, relativePath) = split
        return if (volume.equals(PRIMARY_VOLUME, ignoreCase = true)) {
            File(Environment.getExternalStorageDirectory(), relativePath)
        } else {
            File("/storage/$volume", relativePath)
        }
    }

    private fun fromDownloadsDocument(context: Context, uri: Uri): File? {
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        return if (docId.startsWith(RAW_PREFIX)) {
            File(docId.removePrefix(RAW_PREFIX))
        } else {
            fromMediaStoreData(context, uri)
        }
    }

    private fun fromMediaStoreData(context: Context, uri: Uri): File? {
        return runCatching {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)?.let { File(it) }
                } else {
                    null
                }
            }
        }.getOrNull()
    }
}
