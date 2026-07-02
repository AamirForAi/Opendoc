/*
 *   MJ PDF
 *   Copyright (C) 2023 Mudlej
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *  --------------------------
 *  This code was previously licensed under
 *
 *  MIT License
 *
 *  Copyright (c) 2018 Gokul Swaminathan
 *  Copyright (c) 2023 Mudlej
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package com.gitlab.mudlej.MjPdfReader.ui.about

import android.R
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityAboutBinding
import com.gitlab.mudlej.MjPdfReader.ui.main.MainIntroActivity
import com.gitlab.mudlej.MjPdfReader.ui.showAppFeaturesDialog
import com.gitlab.mudlej.MjPdfReader.util.ColorUtil
import com.gitlab.mudlej.MjPdfReader.util.emailIntent
import com.gitlab.mudlej.MjPdfReader.util.getAppVersion
import com.gitlab.mudlej.MjPdfReader.util.linkIntent
import com.gitlab.mudlej.MjPdfReader.util.navIntent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class AboutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAboutBinding
    
    private val APP_VERSION_RELEASE = "Version " + getAppVersion()
    private val APP_VERSION_DEBUG = "Version " + getAppVersion() + "-debug"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(
            layoutInflater
        )
        setContentView(binding.root)
        initUi()
    }

    private fun initUi() {
        setVersionText()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        ColorUtil.colorize(this, window, supportActionBar)
    }

    private fun setVersionText() {
        // check if app is debug
        if (BuildConfig.DEBUG) {
            binding.versionTextView.text = APP_VERSION_DEBUG
        } else {   //if app is release
            binding.versionTextView.text = APP_VERSION_RELEASE
        }
    }

    fun replayIntro(v: View?) {
        //navigate to intro class (replay the intro)
        startActivity(navIntent(applicationContext, MainIntroActivity::class.java))
    }

    fun showLog(v: View?) {
        showAppFeaturesDialog(this)
    }

    fun showPrivacy(v: View?) {
        PrivacyInfoDialog().show(supportFragmentManager, "privacy_dialog")
    }

    fun showLicense(v: View?) {
        startActivity(
            linkIntent("https://gitlab.com/mudlej_android/mj_pdf_reader/-/blob/main/LICENSE")
        )
    }

    fun showLibraries(v: View?) {
        OpenSourceLibrariesDialog().show(supportFragmentManager, OpenSourceLibrariesDialog.TAG)
    }

    fun emailDev(v: View?) {
        val email = "mudlej@proton.me"
        try {
            startActivity(emailIntent(
                email,
                getString(com.gitlab.mudlej.MjPdfReader.R.string.mj_app_name),
                APP_VERSION_RELEASE
            ))
        } catch (e: ActivityNotFoundException) {
            //Toast.makeText(this, email, Toast.LENGTH_SHORT).show()
            Snackbar.make(binding.root, email, Snackbar.LENGTH_SHORT).show()
        }
    }

    fun navToGit(v: View?) {
        startActivity(linkIntent("https://gitlab.com/mudlej"))
    }

    fun navToSourceCode(v: View?) {
        startActivity(linkIntent("https://gitlab.com/mudlej_android/mj_pdf_reader"))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.home) {
            onBackPressed()
            return true
        }
        return false
    }

    class PrivacyInfoDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val builder = MaterialAlertDialogBuilder(requireContext())
            return builder.setTitle(com.gitlab.mudlej.MjPdfReader.R.string.privacy)
                .setMessage(com.gitlab.mudlej.MjPdfReader.R.string.privacy_info)
                .setPositiveButton(com.gitlab.mudlej.MjPdfReader.R.string.ok) { dialog, _ -> dialog.dismiss() }
                .setIcon(com.gitlab.mudlej.MjPdfReader.R.drawable.privacy_icon)
                .create()
        }
    }
}
