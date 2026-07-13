// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.PDF

fun AppCompatActivity.setupScreenChrome() {
    ColorUtil.colorize(this, window, supportActionBar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
}

fun AppCompatActivity.applyIncognitoTheme() {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    theme.applyStyle(R.style.IncognitoThemeOverlay, true)
}

fun AppCompatActivity.applyIncognitoThemeFromIntent() {
    if (intent.getBooleanExtra(PDF.incognitoKey, false)) {
        applyIncognitoTheme()
    }
}
