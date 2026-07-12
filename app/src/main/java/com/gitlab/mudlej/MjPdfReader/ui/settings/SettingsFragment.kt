// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import android.widget.Toast
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.core.io.restartApplication
import com.gitlab.mudlej.MjPdfReader.data.AutoBackupScheduler
import com.gitlab.mudlej.MjPdfReader.data.BackupData
import com.gitlab.mudlej.MjPdfReader.data.BackupExportOptions
import com.gitlab.mudlej.MjPdfReader.data.BackupFolder
import com.gitlab.mudlej.MjPdfReader.data.BackupManager
import com.gitlab.mudlej.MjPdfReader.data.HistoryCleaner
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationJournal
import com.gitlab.mudlej.MjPdfReader.data.signature.SignatureStore
import com.gitlab.mudlej.MjPdfReader.data.translation.DictionaryInstaller
import com.gitlab.mudlej.MjPdfReader.data.translation.DictionaryStore
import com.gitlab.mudlej.MjPdfReader.core.ui.confirmDialog
import com.gitlab.mudlej.MjPdfReader.ui.home.CoverCache
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class SettingsFragment : PreferenceFragmentCompat() {

    interface Navigation {
        fun onSettingsPageSelected(page: SettingsPage)
    }

    private var navigation: Navigation? = null
    private lateinit var preferenceFactory: SettingsPreferenceFactory
    private lateinit var appPreferences: Preferences
    private var searchQuery = ""
    private var pendingExportToFolder = false
    private var pendingAutoBackupEnable = false
    private var exportOptions = BackupExportOptions(
        includeSettings = true,
        includeHistory = true,
        includePasswords = false,
    )
    private val backupManager by lazy {
        val appContext = requireContext().applicationContext
        BackupManager(appContext, PdfRepository(AppDatabase.getInstance(appContext)))
    }
    private val historyCleaner by lazy {
        val appContext = requireContext().applicationContext
        HistoryCleaner(
            PdfRepository(AppDatabase.getInstance(appContext)),
            AnnotationJournal(appContext),
            SignatureStore(appContext),
            CoverCache.getInstance(appContext),
        )
    }
    private val backupFolderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            onBackupFolderPicked(uri)
        } else {
            pendingExportToFolder = false
            if (pendingAutoBackupEnable) {
                pendingAutoBackupEnable = false
                appPreferences.setAutoBackupEnabled(false)
                rebuildPreferences()
            }
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
        if (savedInstanceState != null) {
            exportOptions = BackupExportOptions(
                includeSettings = savedInstanceState.getBoolean(STATE_EXPORT_SETTINGS, true),
                includeHistory = savedInstanceState.getBoolean(STATE_EXPORT_HISTORY, true),
                includePasswords = savedInstanceState.getBoolean(STATE_EXPORT_PASSWORDS, false),
            )
            pendingExportToFolder = savedInstanceState.getBoolean(STATE_EXPORT_PENDING, false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_EXPORT_SETTINGS, exportOptions.includeSettings)
        outState.putBoolean(STATE_EXPORT_HISTORY, exportOptions.includeHistory)
        outState.putBoolean(STATE_EXPORT_PASSWORDS, exportOptions.includePasswords)
        outState.putBoolean(STATE_EXPORT_PENDING, pendingExportToFolder)
        super.onSaveInstanceState(outState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        appPreferences = Preferences(requireNotNull(preferenceManager.sharedPreferences))
        preferenceFactory = SettingsPreferenceFactory(this, appPreferences)
        rebuildPreferences()
    }

    fun setSearchQuery(query: String) {
        searchQuery = query
        if (isAdded && ::preferenceFactory.isInitialized && page == null) {
            rebuildPreferences()
        }
    }

    fun refreshPreferences() = rebuildPreferences()

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
        showBackupExportOptionsDialog(requireContext()) { options ->
            exportOptions = options
            runBackupExport()
        }
    }

    fun startPickBackupFolder() {
        val initialUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", "primary:Documents")
        backupFolderPickerLauncher.launch(initialUri)
    }

    private fun onBackupFolderPicked(uri: android.net.Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { requireContext().contentResolver.takePersistableUriPermission(uri, flags) }
        appPreferences.setBackupFolderTreeUri(uri.toString())
        rebuildPreferences()
        if (pendingExportToFolder) {
            pendingExportToFolder = false
            runBackupExport()
        }
        if (pendingAutoBackupEnable) {
            pendingAutoBackupEnable = false
            enableAutoBackup()
        }
    }

    fun onAutoBackupToggled(enabled: Boolean) {
        if (!enabled) {
            pendingAutoBackupEnable = false
            AutoBackupScheduler.cancel(requireContext())
            return
        }
        if (appPreferences.getBackupFolderTreeUri() == null) {
            pendingAutoBackupEnable = true
            startPickBackupFolder()
        } else {
            enableAutoBackup()
        }
    }

    private fun enableAutoBackup() {
        AutoBackupScheduler.schedule(
            requireContext(),
            appPreferences.getAutoBackupHour(),
            appPreferences.getAutoBackupMinute(),
        )
        startPickAutoBackupTime()
    }

    fun startPickAutoBackupTime() {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(requireContext())
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(if (is24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(appPreferences.getAutoBackupHour())
            .setMinute(appPreferences.getAutoBackupMinute())
            .setTitleText(R.string.auto_backup_time_title)
            .build()
        picker.addOnPositiveButtonClickListener {
            appPreferences.setAutoBackupTime(picker.hour, picker.minute)
            if (appPreferences.getAutoBackupEnabled()) {
                AutoBackupScheduler.schedule(requireContext(), picker.hour, picker.minute)
            }
            rebuildPreferences()
        }
        picker.show(childFragmentManager, "autoBackupTimePicker")
    }

    fun startBackupImport() {
        importBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
    }

    fun startClearReadingHistory() {
        confirmDialog(
            requireContext(),
            R.string.clear_reading_history_title,
            getString(R.string.clear_reading_history_message),
            R.string.delete,
        ) {
            runClearAction { historyCleaner.clearReadingHistory() }
        }
    }

    fun startClearSavedPasswords() {
        confirmDialog(
            requireContext(),
            R.string.clear_saved_passwords_title,
            getString(R.string.clear_saved_passwords_message),
            R.string.delete,
        ) {
            runClearAction { historyCleaner.clearSavedPasswords() }
        }
    }

    fun startClearBookmarks() {
        confirmDialog(
            requireContext(),
            R.string.clear_bookmarks_title,
            getString(R.string.clear_bookmarks_message),
            R.string.delete,
        ) {
            runClearAction { historyCleaner.clearBookmarks() }
        }
    }

    fun startClearAnnotationJournals() {
        confirmDialog(
            requireContext(),
            R.string.clear_annotation_journals_title,
            getString(R.string.clear_annotation_journals_message),
            R.string.delete,
        ) {
            runClearAction {
                historyCleaner.clearAnnotationJournalsAndSignature()
                null
            }
        }
    }

    fun startDictionaryInstall() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dictionary_download_title)
            .setMessage(
                getString(
                    R.string.dictionary_download_message,
                    Formatter.formatShortFileSize(requireContext(), DictionaryInstaller.downloadSizeBytes),
                    Formatter.formatShortFileSize(requireContext(), DictionaryInstaller.installedSizeBytes),
                )
            )
            .setPositiveButton(R.string.dictionary_download_action) { _, _ -> runDictionaryDownload() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun startDictionaryDelete() {
        confirmDialog(
            requireContext(),
            R.string.dictionary_delete_title,
            getString(R.string.dictionary_delete_message),
            R.string.delete,
        ) {
            val appContext = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) { DictionaryStore.delete(appContext) }
                if (isAdded) {
                    rebuildPreferences()
                }
            }
        }
    }

    private fun runDictionaryDownload() {
        val density = resources.displayMetrics.density
        val progressIndicator = LinearProgressIndicator(requireContext()).apply {
            isIndeterminate = true
        }
        val container = FrameLayout(requireContext()).apply {
            setPadding((24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt(), 0)
            addView(
                progressIndicator,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
            )
        }
        var job: Job? = null
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dictionary_downloading)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(false)
            .create()
        dialog.setOnDismissListener { job?.cancel() }
        dialog.show()
        val appContext = requireContext().applicationContext
        job = viewLifecycleOwner.lifecycleScope.launch {
            val result = DictionaryInstaller.install(appContext) { percent ->
                progressIndicator.post {
                    progressIndicator.isIndeterminate = false
                    progressIndicator.progress = percent
                }
            }
            dialog.dismiss()
            if (!isAdded) {
                return@launch
            }
            result.fold(
                onSuccess = { rebuildPreferences() },
                onFailure = { error ->
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.dictionary_download_failed)
                        .setMessage(error.localizedMessage ?: error.toString())
                        .setPositiveButton(R.string.ok, null)
                        .show()
                },
            )
        }
    }

    private fun runClearAction(action: suspend () -> Int?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val count = withContext(NonCancellable) { action() }
            if (!isAdded) {
                return@launch
            }
            val message = count?.let { getString(R.string.cleared_entries, it) }
                ?: getString(R.string.cleared_done)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun runBackupExport() {
        viewLifecycleOwner.lifecycleScope.launch {
            val options = exportOptions
            val appContext = requireContext().applicationContext
            val configuredFolderUri = appPreferences.getBackupFolderTreeUri()
            val folder = withContext(Dispatchers.IO) {
                BackupFolder.resolve(appContext, configuredFolderUri)
            }
            if (folder == null) {
                if (configuredFolderUri != null) {
                    Toast.makeText(requireContext(), R.string.backup_folder_unavailable, Toast.LENGTH_SHORT).show()
                }
                pendingExportToFolder = true
                startPickBackupFolder()
                return@launch
            }
            val result = try {
                Result.success(withContext(NonCancellable + Dispatchers.IO) {
                    val fileName = BackupFolder.newBackupFileName()
                    val file = folder.createFile("application/json", fileName)
                        ?: throw IOException("Cannot create a file in the backup folder")
                    val summary = backupManager.export(file.uri, options)
                    BackupFolder.enforceRetention(folder)
                    summary to (file.name ?: fileName)
                })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Result.failure(exception)
            }
            if (!isAdded) {
                return@launch
            }
            result.fold(
                onSuccess = { (summary, fileName) ->
                    val lines = buildList {
                        add(getString(R.string.backup_export_saved_file, fileName))
                        if (options.includeSettings) {
                            add(getString(R.string.backup_export_done_settings, summary.settingsCount))
                        }
                        if (options.includeHistory) {
                            add(getString(R.string.backup_export_done_history, summary.recordsCount, summary.bookmarksCount))
                        }
                    }
                    showBackupResultDialog(R.string.backup_export_title, lines.joinToString("\n"))
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
            val data = try {
                backupManager.parse(uri)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                if (isAdded) {
                    showBackupResultDialog(
                        R.string.backup_import_title,
                        getString(R.string.backup_import_failed, exception.localizedMessage.orEmpty()),
                    )
                }
                return@launch
            }
            if (!isAdded) {
                return@launch
            }
            if (data.includesHistory) {
                showImportWipeWarning { applyImport(data) }
            } else {
                applyImport(data)
            }
        }
    }

    private fun showImportWipeWarning(onConfirm: () -> Unit) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_import_title)
            .setMessage(R.string.backup_import_wipe_warning)
            .setPositiveButton(R.string.backup_import_wipe_action) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            val confirmButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            confirmButton.setTextColor(MaterialColors.getColor(confirmButton, androidx.appcompat.R.attr.colorError))
            confirmButton.isEnabled = false
            val label = getString(R.string.backup_import_wipe_action)
            val countdown = object : CountDownTimer(3000, 250) {
                override fun onTick(millisUntilFinished: Long) {
                    confirmButton.text = "$label (${millisUntilFinished / 1000 + 1})"
                }

                override fun onFinish() {
                    confirmButton.text = label
                    confirmButton.isEnabled = true
                }
            }
            countdown.start()
            dialog.setOnDismissListener { countdown.cancel() }
        }
        dialog.show()
    }

    private fun applyImport(data: BackupData) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = try {
                Result.success(withContext(NonCancellable + Dispatchers.IO) {
                    backupManager.importReplace(data, historyCleaner)
                })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Result.failure(exception)
            }
            if (!isAdded) {
                return@launch
            }
            result.fold(
                onSuccess = {
                    AutoBackupScheduler.cancel(requireContext())
                    restartApplication(requireActivity())
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
        private const val STATE_EXPORT_SETTINGS = "settingsExportIncludeSettings"
        private const val STATE_EXPORT_HISTORY = "settingsExportIncludeHistory"
        private const val STATE_EXPORT_PASSWORDS = "settingsExportIncludePasswords"
        private const val STATE_EXPORT_PENDING = "settingsExportPendingFolderPick"

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
