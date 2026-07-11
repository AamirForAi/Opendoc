package com.gitlab.mudlej.MjPdfReader.ui.main

import android.content.pm.ActivityInfo
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.manager.autoscroll.AutoScrollManager
import com.gitlab.mudlej.MjPdfReader.manager.fullscreen.FullScreenOptionsManager
import com.gitlab.mudlej.MjPdfReader.ui.showHowToExitFullscreenDialog
import com.gitlab.mudlej.MjPdfReader.util.ColorUtil

class FullscreenController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val vm: ReaderViewModel,
    private val pref: Preferences,
    private val fullScreenOptionsManager: FullScreenOptionsManager,
    private val autoScrollManager: AutoScrollManager,
    private val zoomSwipeLockController: ZoomSwipeLockController,
    private val brightnessController: BrightnessController,
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
        ColorUtil.exitFullscreen(activity, activity.window, activity.supportActionBar)
        activity.supportActionBar?.show()
        binding.appBarBottomShadow.visibility = View.VISIBLE
        if (pref.getSecondBarEnabled()) {
            updateShortcutBarVisibility()
        }
    }

    private fun hideSystemUi() {
        activity.supportActionBar?.hide()
        binding.appBarBottomShadow.visibility = View.GONE
        binding.secondBarScrollView.visibility = View.GONE
        ColorUtil.enterFullscreen(activity.window)
    }

    private fun unlockScreenOrientation() {
        // set orientation to unspecified so that the screen rotation will be unlocked
        // this is because PORTRAIT / LANDSCAPE modes will lock the app in them
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
