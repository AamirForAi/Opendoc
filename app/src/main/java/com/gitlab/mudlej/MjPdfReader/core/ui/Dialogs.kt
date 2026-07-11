// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.ui

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
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.ui.reader.DocumentState
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.databinding.PasswordDialogBinding
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchActivity
import com.gitlab.mudlej.MjPdfReader.core.ui.copyToClipboard
import com.gitlab.mudlej.MjPdfReader.core.io.convertDateString
import com.gitlab.mudlej.MjPdfReader.core.io.sizeInMb
import androidx.preference.PreferenceManager
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gitlab.mudlej.MjPdfReader.core.ui.AppSnackbar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.shockwave.pdfium.PdfDocument
import java.io.File
import java.util.Locale

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
                AppSnackbar.make(view, activity.getString(R.string.no_input), Snackbar.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            query.toIntOrNull()?.let { pageNumber ->
                goToPageFunc(pageNumber - 1)
            }

            dialog.dismiss()
        }
        .setNegativeButton(activity.getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
        .show()
}
