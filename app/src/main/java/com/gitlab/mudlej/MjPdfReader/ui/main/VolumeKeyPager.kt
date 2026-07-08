package com.gitlab.mudlej.MjPdfReader.ui.main

import android.view.KeyEvent
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding

class VolumeKeyPager(
    private val binding: ActivityMainBinding,
    private val pdf: PDF,
    private val pref: Preferences,
) {

    fun handleKeyDown(keyCode: Int): Boolean {
        if (!pref.getTurnPageByVolumeButtons()) {
            return false
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                binding.pdfView.jumpTo(pdf.pageNumber + 1)
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                binding.pdfView.jumpTo(pdf.pageNumber - 1)
                true
            }
            else -> false
        }
    }
}
