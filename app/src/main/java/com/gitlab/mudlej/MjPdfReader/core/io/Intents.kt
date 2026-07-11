// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

fun plainTextShareIntent(chooserTitle: String, text: String): Intent {
    val intent = Intent(Intent.ACTION_SEND)
    intent.type = "text/plain"
    intent.putExtra(Intent.EXTRA_TEXT, text)
    return Intent.createChooser(intent, chooserTitle)
}
fun fileShareIntent(chooserTitle: String, fileName: String, fileUri: Uri): Intent {
    val intent = Intent(Intent.ACTION_SEND)
    intent.type = "application/pdf"
    intent.putExtra(Intent.EXTRA_STREAM, fileUri)
    intent.clipData = ClipData(fileName, arrayOf("application/pdf"), ClipData.Item(fileUri))
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return Intent.createChooser(intent, chooserTitle)
}
fun imageShareIntent(chooserTitle: String, fileName: String, fileUri: Uri): Intent {
    val intent = Intent(Intent.ACTION_SEND)
    intent.type = "image/*"
    intent.putExtra(Intent.EXTRA_STREAM, fileUri)
    intent.clipData = ClipData(fileName, arrayOf("image/*"), ClipData.Item(fileUri))
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return Intent.createChooser(intent, chooserTitle)
}
fun navIntent(context: Context, activity: Class<*>) = Intent(context, activity)
