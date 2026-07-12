// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.controls

import android.content.pm.ActivityInfo
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import com.gitlab.mudlej.MjPdfReader.ui.reader.showHowToExitFullscreenDialog
import com.gitlab.mudlej.MjPdfReader.core.ui.ColorUtil

class FullscreenController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val vm: ReaderViewModel,
    private val pref: Preferences,
    private val fullScreenOptionsManager: FullScreenOptionsManager,
    private val autoScrollManager: AutoScrollManager,
    private val zoomSwipeLockController: ZoomSwipeLockController,
    private val brightnessController: BrightnessController,
    private val topBarColor: () -> Int?,
    private val updateShortcutBarVisibility: () -> Unit,
) {

    fun toggleFullscreen() {
        if (!vm.isFullScreenToggled) {
            hideSystemUi()
            vm.isFullScreenToggled = true
            fullScreenOptionsManager.hideAll()

            if (pref.getShowFeaturesDialog()) {
                showHowToExitFullscreenDialog(activity, pref)
            }
        }
        else {
            vm.isFullScreenToggled = false
            showSystemUi()
            fullScreenOptionsManager.showAllTemporarilyOrHide()
        }
    }

    fun exitFullscreen() {
        if (!pref.getAlwaysHorizontal()) {
            unlockScreenOrientation()
        }
        toggleFullscreen()
        autoScrollManager.stop()
        zoomSwipeLockController.enableZooming()
        brightnessController.hideControl()
        autoScrollManager.hideControls()
        zoomSwipeLockController.enableHorizontalSwiping()
    }

    fun reapplyStateAfterLoad() {
        if (vm.isFullScreenToggled) {
            hideSystemUi()
        }
    }

    fun checkAutoFullScreen() {
        if (pref.getAutoFullScreen() && !vm.isFullScreenToggled) {
            toggleFullscreen()
        }
    }

    fun restoreFullScreenIfNeeded() {
        if (vm.isFullScreenToggled) {
            vm.isFullScreenToggled = false
            toggleFullscreen()
        }
    }

    fun refreshOnWindowFocus(hasFocus: Boolean) {
        if (hasFocus && vm.isFullScreenToggled) {
            ColorUtil.enterFullscreen(activity.window)
        }
    }

    private fun showSystemUi() {
        ColorUtil.exitFullscreen(activity, activity.window, activity.supportActionBar, topBarColor())
        activity.supportActionBar?.show()
        binding.appBarBottomShadow.visibility = View.VISIBLE
        if (pref.getSecondBarEnabled()) {
            updateShortcutBarVisibility()
        }
        binding.pdfView.scrollHandle?.setTopReachLimit(0)
    }

    private fun hideSystemUi() {
        activity.supportActionBar?.hide()
        binding.appBarBottomShadow.visibility = View.GONE
        binding.secondBarScrollView.visibility = View.GONE
        ColorUtil.enterFullscreen(activity.window)
        binding.pdfView.scrollHandle?.setTopReachLimit(statusBarInset())
    }

    private fun statusBarInset(): Int {
        val insets = ViewCompat.getRootWindowInsets(binding.root) ?: return 0
        return insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
    }

    private fun unlockScreenOrientation() {
        // set orientation to unspecified so that the screen rotation will be unlocked
        // this is because PORTRAIT / LANDSCAPE modes will lock the app in them
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
