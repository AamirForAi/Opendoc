// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.ui

import androidx.appcompat.app.AppCompatActivity

fun AppCompatActivity.setupScreenChrome() {
    ColorUtil.colorize(this, window, supportActionBar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
}
