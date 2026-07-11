// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.about

import android.R
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.core.ui.setupScreenChrome
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityAboutBinding
import com.gitlab.mudlej.MjPdfReader.ui.intro.MainIntroActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.showAppFeaturesDialog
import com.gitlab.mudlej.MjPdfReader.core.io.navIntent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gitlab.mudlej.MjPdfReader.core.ui.AppSnackbar
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
        setupScreenChrome()
        initUi()
    }

    private fun initUi() {
        setVersionText()
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
            AppSnackbar.make(binding.root, email, Snackbar.LENGTH_SHORT).show()
        }
    }

    fun navToGit(v: View?) {
        startActivity(linkIntent("https://gitlab.com/mudlej"))
    }

    fun navToSourceCode(v: View?) {
        startActivity(linkIntent("https://gitlab.com/mudlej_android/mj_pdf_reader"))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
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

fun emailIntent(emailAddress: String, subject: String, text: String): Intent {
    val email = Intent(Intent.ACTION_SENDTO)
    email.data = Uri.parse("mailto:$emailAddress")
    email.putExtra(Intent.EXTRA_SUBJECT, subject)
    email.putExtra(Intent.EXTRA_TEXT, text)
    return email
}

fun linkIntent(url: String?) = Intent(Intent.ACTION_VIEW, Uri.parse(url))

private fun getAppVersion() = BuildConfig.VERSION_NAME
