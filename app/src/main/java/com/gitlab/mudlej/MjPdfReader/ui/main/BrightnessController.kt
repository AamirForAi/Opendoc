package com.gitlab.mudlej.MjPdfReader.ui.main

import android.app.Activity
import android.provider.Settings
import android.view.View
import android.widget.SeekBar
import androidx.core.view.isVisible
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding

class BrightnessController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val pdf: PDF,
) {

    fun toggleControlVisibility() {
        if (binding.brightnessLayout.isVisible) hideControl() else showControl()
    }

    fun hideControl() {
        binding.brightnessLayout.visibility = View.GONE
        pdf.isBrightnessClicked = false
    }

    fun showControl() {
        binding.brightnessLayout.visibility = View.VISIBLE
        pdf.isBrightnessClicked = true
    }

    fun attachSeekbarListener() {
        // init the seekbar
        val brightness = Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        binding.brightnessSeekBar.progress = brightness
        binding.brightnessPercentage.text = "$brightness%"
        binding.brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (seekBar == null) return
                // Don't override system's brightness if the user didn't manually asked for it
                if (fromUser) updateBrightness(progress)
            }
        })
    }

    private fun updateBrightness(brightness: Int) {
        binding.brightnessPercentage.text = "$brightness%"
        activity.window.attributes.screenBrightness = brightness.toFloat() / 100
        activity.window.attributes = activity.window.attributes // apply it
    }
}
