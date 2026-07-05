/*
 *   MJ PDF
 *   Copyright (C) 2023 Mudlej
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *  --------------------------
 *  This code was previously licensed under
 *
 *  MIT License
 *
 *  Copyright (c) 2018 Gokul Swaminathan
 *  Copyright (c) 2023 Mudlej
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package com.gitlab.mudlej.MjPdfReader.ui

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.text.isDigitsOnly
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.databinding.PasswordDialogBinding
import com.gitlab.mudlej.MjPdfReader.ui.main.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchActivity
import com.gitlab.mudlej.MjPdfReader.util.copyToClipboard
import com.gitlab.mudlej.MjPdfReader.util.convertDateString
import com.gitlab.mudlej.MjPdfReader.util.sizeInMb
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.shockwave.pdfium.PdfDocument
import java.io.File
import java.util.Locale

private const val TAG = "Dialogs"

fun showAppFeaturesDialog(context: Context) {
    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle("${context.resources.getString(R.string.mj_app_name)} ${BuildConfig.VERSION_NAME}")
        .setMessage(context.resources.getString(R.string.what_is_new))
        .setPositiveButton(context.resources.getString(R.string.ok)) { dialog, _ -> dialog.dismiss() }
        .create()

    try {
        dialog.show()
    }
    catch (e: Throwable) {
        Log.e(TAG, "showAppFeaturesDialog: Error showing the dialog.(${e.message})")
    }
}

fun showMetaDialog(context: Context, meta: PdfDocument.Meta?, file: File?) {
    if (meta == null) {
        Toast.makeText(context, "Cannot read PDF's meta data!", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.file_properties)
            .setView(createMetadataView(context, meta, file))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
    catch (throwable: Throwable) {
        Log.e(TAG, "showMetaDialog: Failed to show File Properties Dialog", throwable)
        Toast.makeText(context, "Failed to show file properties", Toast.LENGTH_SHORT).show()
    }
}

private fun createMetadataView(context: Context, meta: PdfDocument.Meta, file: File?): View {
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 24), dp(context, 8), dp(context, 24), 0)
    }

    addMetadataRow(content, R.string.pdf_file_name, file?.name)
    addMetadataRow(content, R.string.pdf_title, meta.title)
    addMetadataRow(content, R.string.pdf_author, meta.author)
    addMetadataRow(content, R.string.pdf_pages, String.format(Locale.getDefault(), "%d", meta.totalPages))
    addMetadataRow(content, R.string.pdf_subject, meta.subject)
    addMetadataRow(content, R.string.pdf_keywords, meta.keywords)
    addMetadataRow(content, R.string.pdf_created, convertDateString(meta.creationDate) ?: meta.creationDate)
    addMetadataRow(content, R.string.pdf_modified, convertDateString(meta.modDate) ?: meta.modDate)
    addMetadataRow(content, R.string.pdf_created_by, meta.creator)
    addMetadataRow(content, R.string.pdf_produced_by, meta.producer)
    addMetadataRow(content, R.string.pdf_file_size, file?.let { String.format(Locale.US, "%.2f MB", it.sizeInMb) } ?: "--")

    return ScrollView(context).apply { addView(content) }
}

private fun addMetadataRow(parent: LinearLayout, labelRes: Int, value: String?) {
    if (value.isNullOrBlank()) return

    val context = parent.context
    parent.addView(
        TextView(context).apply {
            text = context.getString(labelRes)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(context, 10), 0, 0)
        }
    )
    parent.addView(
        TextView(context).apply {
            text = value
            setTextIsSelectable(true)
            setPadding(0, dp(context, 2), 0, 0)
        }
    )
}

private fun dp(context: Context, value: Int): Int {
    return (value * context.resources.displayMetrics.density).toInt()
}

fun showHowToExitFullscreenDialog(context: Context, pref: Preferences) {
    MaterialAlertDialogBuilder(context)
        .setTitle(context.getString(R.string.exit_fullscreen_title))
        .setMessage(context.getString(R.string.exit_fullscreen_message))
        .setPositiveButton(context.getString(R.string.exit_fullscreen_positive)) { _, _ ->
            pref.setShowFeaturesDialog(false)
        }
        .setNegativeButton(context.getString(R.string.ok)) {
                dialog: DialogInterface, _ -> dialog.dismiss()
        }
        .create()
        .show()
}

fun showAskForPasswordDialog(
    context: Context,
    pdf: PDF,
    dialogBinding: PasswordDialogBinding,
    displayFunc: (Uri?, Boolean) -> Unit
) {
    val alert = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.protected_pdf)
        .setView(dialogBinding.root)
        .setIcon(R.drawable.lock_icon)
        .setPositiveButton(R.string.ok) { _, _ ->
            pdf.password = dialogBinding.passwordInput.text.toString()
            displayFunc(pdf.uri, dialogBinding.savePassword.isChecked)
        }
        .create()

    alert.setCanceledOnTouchOutside(false)
    alert.show()
}

fun showBookmarksDialog(activity: MainActivity, pdfView: PDFView) {
    // get bookmarks or set an appropriate message for the user
    var bookmarks = pdfView.tableOfContents.map { "${it.title} - P${it.pageIdx + 1}" }

    if (bookmarks.isEmpty()) bookmarks = listOf(activity.getString(R.string.no_bookmarks))

    // create and show the bookmarks dialog
    MaterialAlertDialogBuilder(activity)
        .setTitle(activity.getString(R.string.bookmarks))
        .setItems(bookmarks.toTypedArray()) { dialog, which ->
            if (pdfView.tableOfContents.isEmpty()) return@setItems

            val page = pdfView.tableOfContents[which].pageIdx
            pdfView.jumpTo(page.toInt())
            dialog.dismiss()
        }
        .show()
}

fun showCopyPageTextDialog(
    activity: MainActivity,
    binding: ActivityMainBinding,
    pageNumber: Int,
    pageText: String,
) {
    // create a custom view to make the text selectable
    val pageTextView = TextView(activity)
    pageTextView.setPadding(30, 20, 30, 0)
    pageTextView.setTextIsSelectable(true)
    pageTextView.textSize = 18f
    pageTextView.text = pageText

    val scrollView = ScrollView(activity)
    scrollView.addView(pageTextView)
    //scrollView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
    //scrollView.scrollBarSize = 2

    MaterialAlertDialogBuilder(activity)
        .setView(scrollView)
        .setTitle("${activity.getString(R.string.selectable_text)} #${pageNumber + 1}")
        .setNegativeButton(activity.getString(R.string.close)) { dialog, _ -> dialog.dismiss() }
        .setPositiveButton(activity.getString(R.string.copy_all)) { dialog, _ ->
            val copyLabel = "${activity.getString(R.string.page)} #${pageNumber} Text"
            copyToClipboard(activity, copyLabel, pageText)
            dialog.dismiss()
        }
        .show()
}

fun showSearchDialog(activity: Activity, pdf: PDF) {
    val searchLayout = LayoutInflater.from(activity).inflate(R.layout.input_layout, null) as TextInputLayout
    MaterialAlertDialogBuilder(activity)
        .setTitle(activity.getString(R.string.search))
        .setMessage(activity.getString(R.string.search_dialog_message))
        .setView(searchLayout)
        .setPositiveButton(activity.getText(R.string.search)) { searchDialog, _ ->
            val query = searchLayout.editText?.text ?: return@setPositiveButton
            val queryText = query.toString().trim()
            fun startSearchActivity() {
                Intent(activity, SearchActivity::class.java).also { searchIntent ->
                    searchIntent.putExtra(PDF.filePathKey, pdf.uri.toString())
                    searchIntent.putExtra(PDF.passwordKey, pdf.password)
                    pdf.fileHash?.let { searchIntent.putExtra(PDF.fileHashKey, it) }
                    searchIntent.putExtra(PDF.searchQueryKey, queryText)
                    pdf.lastQuery = queryText
                    activity.startActivityForResult(searchIntent, PDF.startSearchActivity)
                }
            }
            if (queryText.isBlank() || queryText.length < PDF.MIN_SEARCH_QUERY) {
                MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.too_short_query))
                    .setMessage(activity.getString(R.string.too_short_query_message).format(queryText))
                    .setNeutralButton(activity.getString(R.string.proceed_anyway)) { _, _ ->
                        startSearchActivity()
                    }
                    .setPositiveButton(activity.getText(R.string.ok)) { badQueryDialog, _ ->
                        searchDialog.dismiss()
                        badQueryDialog.dismiss()
                        showSearchDialog(activity, pdf)
                    }
                    .show()
            }
            else {
                startSearchActivity()
            }
        }
        .setNegativeButton(activity.getText(R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }
        .show()
}

fun showGoToPageDialog(
    activity: Activity,
    view: View,
    pageIndex: Int,
    pdfLength: Int,
    goToPageFunc: (Int) -> Unit
) {
    // create EditText for input
    val inputLayout = LayoutInflater
        .from(activity)
        .inflate(R.layout.only_integers_input_layout, null) as TextInputLayout

    inputLayout.hint = "Current page ${pageIndex + 1}/$pdfLength"

    MaterialAlertDialogBuilder(activity)
        .setTitle(activity.getString(R.string.go_to_page))
        .setView(inputLayout)
        .setPositiveButton(activity.getString(R.string.go_to)) { dialog, _ ->
            val query = inputLayout.editText?.text.toString().lowercase().trim()

            // check if the user provided input
            if (query.isEmpty()) {
                //Toast.makeText(activity, activity.getString(R.string.no_input), Toast.LENGTH_SHORT).show()
                Snackbar.make(view, activity.getString(R.string.no_input), Snackbar.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            if (query.isDigitsOnly())
                goToPageFunc(query.toInt() - 1)

            dialog.dismiss()
        }
        .setNegativeButton(activity.getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
        .show()
}
