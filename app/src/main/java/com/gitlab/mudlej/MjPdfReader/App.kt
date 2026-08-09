// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.gitlab.mudlej.MjPdfReader.core.crash.ConsentPluginLoader
import com.gitlab.mudlej.MjPdfReader.data.AutoBackupScheduler
import com.gitlab.mudlej.MjPdfReader.data.OnlineDocumentStore
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.PrintController
import com.gitlab.mudlej.MjPdfReader.ui.reader.controls.PdfThemeController
import com.google.android.material.color.DynamicColors
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.dialog
import org.acra.config.httpSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

class App : Application() {
    override fun onCreate() {
        DynamicColors.applyToActivitiesIfAvailable(this)
        super.onCreate()
        if (!ACRA.isACRASenderServiceProcess()) {
            val preferences = Preferences(PreferenceManager.getDefaultSharedPreferences(this))
            AppCompatDelegate.setDefaultNightMode(PdfThemeController.interfaceNightMode(preferences))
            if (preferences.getAutoBackupEnabled()) {
                AutoBackupScheduler.ensureScheduled(this, preferences.getAutoBackupHour(), preferences.getAutoBackupMinute())
            }
            OnlineDocumentStore.sweepIncognito(this)
            PrintController.sweepStaging(this)
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON
            pluginLoader = ConsentPluginLoader()
            reportContent = listOf(
                ReportField.STACK_TRACE,
                ReportField.APP_VERSION_CODE,
                ReportField.APP_VERSION_NAME,
                ReportField.USER_CRASH_DATE,
                ReportField.PACKAGE_NAME,
                ReportField.ANDROID_VERSION,
                ReportField.PHONE_MODEL,
                ReportField.LOGCAT,
                ReportField.AVAILABLE_MEM_SIZE,
            )
            httpSender {
                uri = getString(R.string.CRASH_REPORT_API_URI)
                httpHeaders = mapOf(
                    "X-Parse-Application-Id" to getString(R.string.CRASH_REPORT_APP_ID),
                    "X-Parse-REST-API-Key" to getString(R.string.CRASH_REPORT_API_KEY)
                )
            }
            dialog {
                title = getString(R.string.send_crash_report_title)
                text = "${getString(R.string.send_crash_report_title)}\n\n${getString(R.string.send_crash_report_message)}"
                positiveButtonText = getString(R.string.send)
                negativeButtonText = getString(R.string.no)
            }
        }
    }
}
