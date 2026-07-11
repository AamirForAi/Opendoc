package com.gitlab.mudlej.MjPdfReader.core.ui

import androidx.appcompat.app.AppCompatActivity
import com.gitlab.mudlej.MjPdfReader.util.ColorUtil

fun AppCompatActivity.setupScreenChrome() {
    ColorUtil.colorize(this, window, supportActionBar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
}
