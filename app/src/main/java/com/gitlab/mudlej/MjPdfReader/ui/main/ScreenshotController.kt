package com.gitlab.mudlej.MjPdfReader.ui.main

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.format.DateFormat
import android.view.View
import androidx.core.net.toUri
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.enums.FileType
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Date

class ScreenshotController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val pdf: PDF,
    private val beforeCapture: () -> Unit,
    private val shareImage: (Uri?) -> Unit,
) {

    fun takeScreenshot() {
        val now = DateFormat.format("yyyy_MM_dd-hh_mm_ss", Date())
        try {
            val fileName = "${pdf.name.removeSuffix(".pdf")} - ${now}.jpg"
            val imageFile = File(activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)

            beforeCapture()
            val bitmap = screenShot(binding.pdfView)

            val outputStream = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, PDF.SCREENSHOT_IMAGE_QUALITY, outputStream)
            outputStream.flush()
            outputStream.close()

            val uri = saveImage(bitmap, fileName)
            Snackbar.make(binding.root, activity.getString(R.string.screenshot_saved), Snackbar.LENGTH_SHORT).also {
                it.setAction(activity.getString(R.string.share)) { shareImage(uri) }
                it.show()
            }
        }
        catch (e: Throwable) {
            // Several error may come out with file handling or DOM
            Snackbar.make(binding.root, activity.getString(R.string.failed_save_screenshot), Snackbar.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun screenShot(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    @Throws(IOException::class)
    private fun saveImage(bitmap: Bitmap, fileName: String): Uri? {
        val (fileOutputStream: OutputStream?, imageUri: Uri?) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/*")

            // e.g.     ~/Pictures/app_name/screenshot1.jpg
            contentValues.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/${activity.getString(R.string.mj_app_name)}/"
            )

            val imageUri = activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            Pair(imageUri?.let { activity.contentResolver.openOutputStream(it) }, imageUri)
        }
        else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
            val image = File(imagesDir, fileName)
            Pair(FileOutputStream(image), image.toUri())
        }
        if (fileOutputStream != null) {
            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                PDF.SCREENSHOT_IMAGE_QUALITY,
                fileOutputStream
            )
        }
        fileOutputStream?.close()
        return imageUri
    }
}
