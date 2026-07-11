/*
 *   MJ PDF
 *   Copyright (C) 2023 Mudlej
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 */

package com.gitlab.mudlej.MjPdfReader.ui.settings

import android.content.Context
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.BackupManager
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : PreferenceFragmentCompat() {

    interface Navigation {
        fun onSettingsPageSelected(page: SettingsPage)
    }

    private var navigation: Navigation? = null
    private lateinit var preferenceFactory: SettingsPreferenceFactory
    private var searchQuery = ""
    private var includePasswordsInExport = false
    private val backupManager by lazy {
        val appContext = requireContext().applicationContext
        BackupManager(appContext, PdfRepository(AppDatabase.getInstance(appContext)))
    }
    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runBackupExport(uri)
        }
    }
    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runBackupImport(uri)
        }
    }

    private val page: SettingsPage?
        get() = arguments?.getString(ARG_PAGE)?.let(SettingsPage::valueOf)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        navigation = context as? Navigation
    }

    override fun onDetach() {
        navigation = null
        super.onDetach()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        searchQuery = arguments?.getString(ARG_SEARCH_QUERY).orEmpty()
        includePasswordsInExport = savedInstanceState?.getBoolean(STATE_INCLUDE_PASSWORDS) ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_INCLUDE_PASSWORDS, includePasswordsInExport)
        super.onSaveInstanceState(outState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val preferences = Preferences(requireNotNull(preferenceManager.sharedPreferences))
        preferenceFactory = SettingsPreferenceFactory(this, preferences)
        rebuildPreferences()
    }

    fun setSearchQuery(query: String) {
        searchQuery = query
        if (isAdded && ::preferenceFactory.isInitialized && page == null) {
            rebuildPreferences()
        }
    }

    private fun rebuildPreferences() {
        val screen = preferenceManager.createPreferenceScreen(requireContext())
        val currentPage = page
        if (currentPage == null) {
            buildRootScreen(screen)
        } else {
            buildPageScreen(screen, currentPage)
        }
        preferenceScreen = screen
    }

    private fun buildRootScreen(screen: PreferenceScreen) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            SettingsPage.values().forEach { page ->
                screen.addPreference(preferenceFactory.navigationPreference(page, ::selectPage))
            }
            return
        }

        val results = preferenceFactory.entries().filter { it.matches(requireContext(), query) }
        if (results.isEmpty()) {
            screen.addPreference(preferenceFactory.noSearchResultsPreference())
            return
        }

        results.forEach { entry ->
            screen.addPreference(entry.createPreference(preferenceFactory, getString(entry.page.titleRes)))
        }
    }

    private fun buildPageScreen(screen: PreferenceScreen, page: SettingsPage) {
        preferenceFactory.entriesFor(page).forEach { entry ->
            screen.addPreference(entry.createPreference(preferenceFactory, breadcrumb = null))
        }
    }

    private fun selectPage(page: SettingsPage) {
        navigation?.onSettingsPageSelected(page)
    }

    fun startBackupExport() {
        val checked = booleanArrayOf(false)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_export_title)
            .setMultiChoiceItems(
                arrayOf(getString(R.string.include_saved_passwords)),
                checked,
            ) { _, _, isChecked -> checked[0] = isChecked }
            .setPositiveButton(R.string.backup_export_action) { _, _ ->
                includePasswordsInExport = checked[0]
                exportBackupLauncher.launch(BackupManager.defaultBackupFileName())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun startBackupImport() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_import_title)
            .setMessage(R.string.backup_import_confirm)
            .setPositiveButton(R.string.backup_import_action) { _, _ ->
                importBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun runBackupExport(uri: android.net.Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = try {
                Result.success(withContext(NonCancellable) { backupManager.export(uri, includePasswordsInExport) })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Result.failure(exception)
            }
            if (!isAdded) {
                return@launch
            }
            result.fold(
                onSuccess = { summary ->
                    showBackupResultDialog(
                        R.string.backup_export_title,
                        getString(
                            R.string.backup_export_done,
                            summary.settingsCount,
                            summary.recordsCount,
                            summary.bookmarksCount,
                        ),
                    )
                },
                onFailure = { error ->
                    showBackupResultDialog(
                        R.string.backup_export_title,
                        getString(R.string.backup_export_failed, error.localizedMessage.orEmpty()),
                    )
                },
            )
        }
    }

    private fun runBackupImport(uri: android.net.Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = try {
                Result.success(withContext(NonCancellable) { backupManager.import(uri) })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Result.failure(exception)
            }
            if (!isAdded) {
                return@launch
            }
            result.fold(
                onSuccess = { summary ->
                    showBackupResultDialog(
                        R.string.backup_import_title,
                        getString(
                            R.string.backup_import_done,
                            summary.settingsApplied,
                            summary.recordsInserted,
                            summary.recordsUpdated,
                            summary.bookmarksImported,
                        ),
                    )
                },
                onFailure = { error ->
                    showBackupResultDialog(
                        R.string.backup_import_title,
                        getString(R.string.backup_import_failed, error.localizedMessage.orEmpty()),
                    )
                },
            )
        }
    }

    private fun showBackupResultDialog(titleRes: Int, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    companion object {
        private const val ARG_PAGE = "settingsPage"
        private const val ARG_SEARCH_QUERY = "settingsSearchQuery"
        private const val STATE_INCLUDE_PASSWORDS = "settingsIncludePasswordsInExport"

        fun root(searchQuery: String = ""): SettingsFragment {
            return SettingsFragment().apply {
                arguments = Bundle().apply { putString(ARG_SEARCH_QUERY, searchQuery) }
            }
        }

        fun page(page: SettingsPage): SettingsFragment {
            return SettingsFragment().apply {
                arguments = Bundle().apply { putString(ARG_PAGE, page.name) }
            }
        }
    }
}
