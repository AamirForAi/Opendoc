// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.intro

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.github.paolorotolo.appintro.AppIntro
import com.github.paolorotolo.appintro.AppIntroFragment
import com.github.paolorotolo.appintro.model.SliderPage
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.PermissionManager

class MainIntroActivity : AppIntro() {
    private var themeColor = "#202020"
    var bg = Color.parseColor(themeColor)

    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionManager = PermissionManager(this)
        supportActionBar?.hide()

        val first = SliderPage()
        first.title = getString(R.string.mj_app_name)
        first.description = getString(R.string.description_intro)
        first.imageDrawable = R.drawable.new_logo
        first.bgColor = bg
        addSlide(AppIntroFragment.newInstance(first))

        val second = SliderPage()
        second.title = getString(R.string.title_open)
        second.description = getString(R.string.description_open)
        second.imageDrawable = R.drawable.opensource_logo
        second.bgColor = bg
        addSlide(AppIntroFragment.newInstance(second))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val third = SliderPage()
            third.title = getString(R.string.title_permission)
            third.description = getString(R.string.description_permission)
            third.imageDrawable = R.drawable.patterns_permissions
            third.bgColor = bg
            addSlide(AppIntroFragment.newInstance(third))
            askForPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 3)
        }

        showSkipButton(false)
        showStatusBar(false)
        setNavBarColor(themeColor)
    }

    override fun onDonePressed(currentFragment: Fragment) {
        super.onDonePressed(currentFragment)
        finish()
    }
}