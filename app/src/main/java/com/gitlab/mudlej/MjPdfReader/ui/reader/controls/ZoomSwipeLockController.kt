package com.gitlab.mudlej.MjPdfReader.ui.reader.controls

import android.graphics.drawable.Drawable
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding

class ZoomSwipeLockController(
    private val binding: ActivityMainBinding,
    private val drawableOf: (Int) -> Drawable?,
) {

    fun toggleZoomLock() {
        if (binding.pdfView.isZoomDisabled) {
            enableZooming()
        } else {
            disableZooming()
        }
    }

    fun toggleHorizontalSwipeLock() {
        if (binding.pdfView.isHorizontalSwipeDisabled) {
            enableHorizontalSwiping()
        } else {
            disableHorizontalSwiping()
        }
    }

    fun enableZooming() {
        binding.toggleZoomLockButton.icon = drawableOf(R.drawable.ic_zoom_out)
        binding.pdfView.isZoomDisabled = false
    }

    fun disableZooming() {
        binding.toggleZoomLockButton.icon = drawableOf(R.drawable.ic_lock)
        binding.pdfView.isZoomDisabled = true
    }

    fun enableHorizontalSwiping() {
        binding.toggleHorizontalSwipeButton.icon = drawableOf(R.drawable.ic_allow_horizontal_swipe)
        binding.pdfView.isHorizontalSwipeDisabled = false
    }

    fun disableHorizontalSwiping() {
        binding.toggleHorizontalSwipeButton.icon = drawableOf(R.drawable.ic_horizontal_swipe_locked)
        binding.pdfView.isHorizontalSwipeDisabled = true
    }
}
