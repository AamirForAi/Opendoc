// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import com.gitlab.mudlej.MjPdfReader.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme != null && uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val indexDisplayName: Int = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (indexDisplayName != -1) result = cursor.getString(indexDisplayName)
                }
            }
        } catch (e: Exception) {
            Log.w("getFileName", context.getString(R.string.error_load_file_name), e)
        }
    }

    val name = result ?: uri.lastPathSegment ?: return "Unknown PDF Name"

    // Check https://github.com/mudlej/mj_pdf/issues/24
    if (name.contains("SMB", ignoreCase = true)) {
        return try {
            decodeNameFromUrl(name)
        } catch (throwable: Throwable) {
            name
        }
    }
    return name
}
@Throws(IllegalArgumentException::class)
fun decodeNameFromUrl(encodedUrl: String): String {
    // First, decode the entire URL
    val decodedUrl = try {
        URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
    } catch (e: IllegalArgumentException) {
        encodedUrl
    }

    // Extract the last segment from the decoded URL
    val lastSegment = decodedUrl.substringAfterLast('/')

    // Attempt to decode the last segment again in case of partial decoding
    return try {
        URLDecoder.decode(lastSegment, StandardCharsets.UTF_8.toString())
    } catch (e: IllegalArgumentException) {
        // If decoding fails, attempt to decode up to the last complete percent-encoded sequence
        val safePart = lastSegment.substringBeforeLast('%')
        URLDecoder.decode(safePart, StandardCharsets.UTF_8.toString()) + lastSegment.substringAfterLast('%')
    }
}
@Throws(IOException::class)
fun writeBytesToFile(directory: File, fileName: String, fileContent: ByteArray?) {
    val file = File(directory, fileName)
    FileOutputStream(file).use { stream -> stream.write(fileContent) }
}
fun canWriteToDownloadFolder(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) true
    else ContextCompat.checkSelfPermission(context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
@Throws(IOException::class)
fun readBytesToEnd(inputStream: InputStream): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var bytesRead: Int
    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        output.write(buffer, 0, bytesRead)
    }
    return output.toByteArray()
}
val File.size get() = if (!exists()) 0.0 else length().toDouble()
val File.sizeInKb get() = size / 1024
val File.sizeInMb get() = sizeInKb / 1024
