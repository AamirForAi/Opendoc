// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.input

import android.view.MotionEvent
import com.gitlab.mudlej.MjPdfReader.ui.reader.DocumentState
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.TapToTurnZones
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding

class EdgeTapPager(
    private val binding: ActivityMainBinding,
    private val pdf: DocumentState,
    private val pref: Preferences,
) {

    fun handleTap(event: MotionEvent): Boolean {
        if (!pref.getSinglePageMode()) {
            return false
        }
        val zones = pref.getTapToTurnZones()
        if (zones == TapToTurnZones.OFF) {
            return false
        }
        val pdfView = binding.pdfView
        if (pdfView.zoom > 1f) {
            return false
        }
        val step = when (zones) {
            TapToTurnZones.LEFT_RIGHT -> {
                val forward = if (pdfView.isHorizontalReadingDirectionRtl) -1 else 1
                when {
                    event.x >= pdfView.width * 0.75f -> forward
                    event.x < pdfView.width * 0.25f -> -forward
                    else -> return false
                }
            }
            TapToTurnZones.TOP_BOTTOM -> when {
                event.y >= pdfView.height * 0.75f -> 1
                event.y < pdfView.height * 0.25f -> -1
                else -> return false
            }
            TapToTurnZones.OFF -> return false
        }
        if (pdfView.hasLinkAt(event.x, event.y)) {
            return false
        }
        pdfView.jumpTo(pdfView.getPageAfterRowStep(pdf.pageNumber, step), true)
        return true
    }
}
