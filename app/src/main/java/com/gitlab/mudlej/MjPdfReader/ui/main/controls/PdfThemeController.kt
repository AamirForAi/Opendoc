package com.gitlab.mudlej.MjPdfReader.ui.main.controls

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.util.AppSnackbar
import com.gitlab.mudlej.MjPdfReader.util.ColorUtil
import com.google.android.material.snackbar.Snackbar

class PdfThemeController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val pref: Preferences,
) {

    fun configureTheme() {
        ColorUtil.colorize(activity, activity.window, activity.supportActionBar)
        val color = ColorUtil.getBarColor(activity)
        binding.secondBarScrollView.setBackgroundColor(color)

        applyPdfThemeToView(effectivePdfDarkTheme(), reloadPages = false)

        val appNightMode = when (pref.getInterfaceTheme()) {
            Preferences.themeSystem -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            Preferences.themeDark -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        if (AppCompatDelegate.getDefaultNightMode() != appNightMode) {
            AppCompatDelegate.setDefaultNightMode(appNightMode)
        }
    }

    fun effectivePdfDarkTheme(): Boolean {
        return when (pref.getPdfPagesTheme()) {
            Preferences.themeSystem -> isSystemDarkTheme()
            Preferences.themeDark -> true
            else -> false
        }
    }

    fun switchPdfTheme(hasFile: () -> Boolean) {
        if (pref.getPdfPagesTheme() == Preferences.themeSystem) {
            AppSnackbar.make(
                binding.root,
                activity.getString(R.string.pdf_theme_follows_system),
                Snackbar.LENGTH_LONG
            ).show()
        }
        else if (hasFile()) {
            setPdfTheme(!pref.getPdfDarkTheme())
        }
    }

    private fun setPdfTheme(darkTheme: Boolean) {
        if (pref.getPdfPagesTheme() != Preferences.themeSystem && pref.getPdfDarkTheme() == darkTheme) {
            return
        }
        pref.setPdfPagesTheme(if (darkTheme) Preferences.themeDark else Preferences.themeLight)
        applyPdfThemeToView(darkTheme, reloadPages = true)
    }

    private fun applyPdfThemeToView(darkTheme: Boolean, reloadPages: Boolean) {
        binding.pdfView.setNightMode(darkTheme)
        if (!darkTheme) {
            binding.pdfView.setBackgroundColor(Preferences.pdfDarkBackgroundColor)
        } else {
            binding.pdfView.setBackgroundColor(Preferences.pdfLightBackgroundColor)
        }
        if (reloadPages) {
            binding.pdfView.reloadPages()
        }
    }

    private fun isSystemDarkTheme(): Boolean {
        return when (activity.applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }
}
