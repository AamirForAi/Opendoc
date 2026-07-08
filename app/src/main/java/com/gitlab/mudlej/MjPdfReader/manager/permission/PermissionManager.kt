package com.gitlab.mudlej.MjPdfReader.manager.permission

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        else {
            if (hasLegacyStoragePermission()) {
                storageGrantedFunc()
            }
            else {
                legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        return false;
    }

    private fun hasLegacyStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    private val legacyPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && ::storageGrantedFunc.isInitialized) {
                storageGrantedFunc()
            }
        }

    val requestPermissionLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && ::storageGrantedFunc.isInitialized) {
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
