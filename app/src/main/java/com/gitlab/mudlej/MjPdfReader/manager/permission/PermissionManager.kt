package com.gitlab.mudlej.MjPdfReader.manager.permission

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.ui.main.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder


class PermissionManager(private val activity: AppCompatActivity) {

    private lateinit var storageGrantedFunc: () -> Unit

    // -------------- Manage Storage
    fun checkStoragePermission(func: () -> Unit): Boolean {
        storageGrantedFunc = func

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
                requestPermissionLauncher.launch(intent)
            }
            else {
                storageGrantedFunc()
            }
        }
        return false;
    }

    val requestPermissionLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                MaterialAlertDialogBuilder(activity)
                    .setCancelable(false)
                    .setTitle("Really?")
                    .setMessage("For real? How can I work right now?!")
                    .setPositiveButton("Ask Again") { _, _ -> checkStoragePermission(storageGrantedFunc) }
                    .show()
            }
            else {
                storageGrantedFunc()
            }
        }
    }

    // -------------- File Picker

    fun launchPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/pdf"
        pdfPicker.launch(intent)
    }

    private val pdfPicker = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val pdfUri = result?.data?.data ?: return@registerForActivityResult

            Intent(activity, MainActivity::class.java).also { intent ->
                intent.data = pdfUri
                activity.startActivity(intent)
            }
        }
    }
}
