// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import android.content.DialogInterface
import android.content.Intent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.BackupFolder
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.ui.history.ReadingHistoryActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.core.ui.SegmentedButtonStyler
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

internal class SettingsPreferenceFactory(
    private val fragment: PreferenceFragmentCompat,
    private val appPreferences: Preferences,
) {
    private val context get() = fragment.requireContext()
    private val allEntries by lazy {
        SettingsEntryProvider(appPreferences).entries()
    }

    fun entries(): List<SettingEntry> = allEntries

    fun entriesFor(page: SettingsPage): List<SettingEntry> {
        return allEntries.filter { it.page == page }
    }

    fun navigationPreference(page: SettingsPage, onSelected: (SettingsPage) -> Unit): Preference {
        return Preference(context).apply {
            title = getString(page.titleRes)
            summary = getString(page.summaryRes)
            setIcon(page.iconRes)
            widgetLayoutResource = R.layout.preference_widget_chevron
            isIconSpaceReserved = true
            setOnPreferenceClickListener {
                onSelected(page)
                true
            }
        }
    }

    fun noSearchResultsPreference(): Preference {
        return Preference(context).apply {
            title = getString(R.string.settings_search_no_results)
            isSelectable = false
            isIconSpaceReserved = false
        }
    }

    fun interfaceThemePreference(breadcrumb: String?): Preference {
        return ThemeChoicePreference(
            context = context,
            titleText = formatSummary(breadcrumb, getString(R.string.dark_theme_for_app)) ?: getString(R.string.dark_theme_for_app),
            initialSelectedMode = appPreferences.getInterfaceTheme(),
        ) { mode ->
            appPreferences.setInterfaceTheme(mode)
            setDefaultNightMode(
                when (mode) {
                    Preferences.themeSystem -> MODE_NIGHT_FOLLOW_SYSTEM
                    Preferences.themeDark -> MODE_NIGHT_YES
                    else -> MODE_NIGHT_NO
                }
            )
        }
    }

    fun pdfPagesThemePreference(breadcrumb: String?): Preference {
        return ThemeChoicePreference(
            context = context,
            titleText = formatSummary(breadcrumb, getString(R.string.dark_theme_for_pdf)) ?: getString(R.string.dark_theme_for_pdf),
            initialSelectedMode = appPreferences.getPdfPagesTheme(),
        ) { mode ->
            appPreferences.setPdfPagesTheme(mode)
        }
    }

    fun switchPreference(
        @StringRes titleRes: Int,
        key: String,
        defaultValue: Boolean,
        @StringRes summaryRes: Int?,
        breadcrumb: String?,
    ): SwitchPreferenceCompat {
        return SwitchPreferenceCompat(context).apply {
            title = getString(titleRes)
            this.key = key
            setDefaultValue(defaultValue)
            summary = formatSummary(breadcrumb, summaryRes?.let(::getString))
            isIconSpaceReserved = false
        }
    }

    fun actionPreference(
        @StringRes titleRes: Int,
        key: String,
        defaultValue: String,
        currentValue: String,
        actions: List<ConfigurableAction>,
        breadcrumb: String?,
        onActionSelected: (String) -> Unit,
    ): Preference {
        val resetActionId = defaultValue.takeIf { value -> actions.any { it.id == value } }
            ?: actions.first().id
        var selectedActionId = currentValue.takeIf { value -> actions.any { it.id == value } }
            ?: resetActionId

        return Preference(context).apply {
            title = getString(titleRes)
            this.key = key
            updateActionSummary(selectedActionId, breadcrumb)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                showActionPreferenceDialog(
                    title = getString(titleRes),
                    actions = actions,
                    currentValue = selectedActionId,
                    resetValue = resetActionId,
                ) { actionId ->
                    selectedActionId = actionId
                    onActionSelected(actionId)
                    updateActionSummary(actionId, breadcrumb)
                }
                true
            }
        }
    }

    fun fullScreenButtonsPreference(breadcrumb: String?): Preference {
        return Preference(context).apply {
            title = getString(R.string.fullscreen_buttons)
            key = Preferences.fullScreenOverlayActionsKey
            summary = formatSummary(breadcrumb, getString(R.string.fullscreen_buttons_summary))
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                showFullScreenButtonsPreferenceDialog(context, appPreferences) {}
                true
            }
        }
    }

    fun scrollingInfoCardPreference(breadcrumb: String?): Preference {
        return Preference(context).apply {
            title = getString(R.string.scrolling_info_card)
            key = Preferences.fullScreenInfoShowPageNumberKey
            summary = formatSummary(breadcrumb, getString(R.string.scrolling_info_card_summary))
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                showScrollingInfoCardPreferenceDialog(context, appPreferences)
                true
            }
        }
    }

    fun shortcutBarButtonsPreference(breadcrumb: String?): Preference {
        return Preference(context).apply {
            title = getString(R.string.shortcut_bar_buttons)
            key = Preferences.shortcutBarActionsKey
            summary = formatSummary(breadcrumb, getString(R.string.shortcut_bar_buttons_summary))
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                showShortcutBarButtonsPreferenceDialog(context, appPreferences) {}
                true
            }
        }
    }

    fun openIncognitoPreference(breadcrumb: String?): Preference {
        return Preference(context).apply {
            title = getString(R.string.open_in_incognito)
            key = "openIncognito"
            summary = formatSummary(breadcrumb, getString(R.string.open_in_incognito_summary))
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                val intent = Intent(context, MainActivity::class.java)
                intent.putExtra(PDF.incognitoKey, true)
                context.startActivity(intent)
                true
            }
        }
    }

    fun readingHistoryPreference(breadcrumb: String?): Preference {
        return Preference(context).apply {
            title = getString(R.string.reading_history)
            key = "readingHistory"
            summary = formatSummary(breadcrumb, getString(R.string.reading_history_row_summary))
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                context.startActivity(Intent(context, ReadingHistoryActivity::class.java))
                true
            }
        }
    }

    fun clearReadingHistoryPreference(breadcrumb: String?): Preference {
        return clearActionPreference(
            "clearReadingHistory",
            R.string.clear_reading_history_title,
            R.string.clear_reading_history_summary,
            breadcrumb,
        ) { it.startClearReadingHistory() }
    }

    fun clearSavedPasswordsPreference(breadcrumb: String?): Preference {
        return clearActionPreference(
            "clearSavedPasswords",
            R.string.clear_saved_passwords_title,
            R.string.clear_saved_passwords_summary,
            breadcrumb,
        ) { it.startClearSavedPasswords() }
    }

    fun clearBookmarksPreference(breadcrumb: String?): Preference {
        return clearActionPreference(
            "clearBookmarks",
            R.string.clear_bookmarks_title,
            R.string.clear_bookmarks_summary,
            breadcrumb,
        ) { it.startClearBookmarks() }
    }

    fun clearAnnotationJournalsPreference(breadcrumb: String?): Preference {
        return clearActionPreference(
            "clearAnnotationJournals",
            R.string.clear_annotation_journals_title,
            R.string.clear_annotation_journals_summary,
            breadcrumb,
        ) { it.startClearAnnotationJournals() }
    }

    private fun clearActionPreference(
        preferenceKey: String,
        @StringRes titleRes: Int,
        @StringRes summaryRes: Int,
        breadcrumb: String?,
        onClicked: (SettingsFragment) -> Unit,
    ): Preference {
        val host = fragment as? SettingsFragment
        return Preference(context).apply {
            title = getString(titleRes)
            key = preferenceKey
            summary = formatSummary(breadcrumb, getString(summaryRes))
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                host?.let(onClicked)
                true
            }
        }
    }

    fun backupFolderPreference(breadcrumb: String?): Preference {
        val host = fragment as? SettingsFragment
        val detail = BackupFolder.describe(appPreferences.getBackupFolderTreeUri())
            ?.let { fragment.getString(R.string.backup_folder_summary_set, it) }
            ?: getString(R.string.backup_folder_summary_unset)
        return Preference(context).apply {
            title = getString(R.string.backup_folder_title)
            key = "backupFolder"
            summary = formatSummary(breadcrumb, detail)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                host?.startPickBackupFolder()
                true
            }
        }
    }

    fun autoBackupSwitchPreference(breadcrumb: String?): Preference {
        val host = fragment as? SettingsFragment
        return SwitchPreferenceCompat(context).apply {
            title = getString(R.string.auto_backup_title)
            key = Preferences.autoBackupEnabledKey
            setDefaultValue(Preferences.autoBackupEnabledDefault)
            summary = formatSummary(breadcrumb, getString(R.string.auto_backup_summary))
            isIconSpaceReserved = false
            setOnPreferenceChangeListener { _, newValue ->
                host?.onAutoBackupToggled(newValue == true)
                true
            }
        }
    }

    fun autoBackupTimePreference(breadcrumb: String?): Preference {
        val host = fragment as? SettingsFragment
        val time = LocalTime.of(appPreferences.getAutoBackupHour(), appPreferences.getAutoBackupMinute())
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        val detail = buildList {
            add(fragment.getString(R.string.auto_backup_time_summary, time))
            val lastRun = appPreferences.getAutoBackupLastRun()
            if (lastRun > 0L) {
                val lastRunText = Instant.ofEpochMilli(lastRun)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
                val error = appPreferences.getAutoBackupLastError()
                add(
                    if (error == null) {
                        fragment.getString(R.string.auto_backup_last_success, lastRunText)
                    } else {
                        fragment.getString(R.string.auto_backup_last_failed, error)
                    }
                )
            }
        }.joinToString("\n")
        return Preference(context).apply {
            title = getString(R.string.auto_backup_time_title)
            key = "autoBackupTime"
            summary = formatSummary(breadcrumb, detail)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                host?.startPickAutoBackupTime()
                true
            }
        }
    }

    fun backupExportPreference(breadcrumb: String?): Preference {
        val host = fragment as? SettingsFragment
        return Preference(context).apply {
            title = getString(R.string.backup_export_title)
            key = "backupExport"
            summary = formatSummary(breadcrumb, getString(R.string.backup_export_summary))
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                host?.startBackupExport()
                true
            }
        }
    }

    fun backupImportPreference(breadcrumb: String?): Preference {
        val host = fragment as? SettingsFragment
        return Preference(context).apply {
            title = getString(R.string.backup_import_title)
            key = "backupImport"
            summary = formatSummary(breadcrumb, getString(R.string.backup_import_summary))
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                host?.startBackupImport()
                true
            }
        }
    }

    fun highlightColorsPreference(breadcrumb: String?): Preference {
        return Preference(context).apply {
            title = getString(R.string.highlight_colors)
            key = Preferences.highlightColorsKey
            summary = formatSummary(breadcrumb, getString(R.string.highlight_colors_summary))
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                showHighlightColorsPreferenceDialog(context, appPreferences) {}
                true
            }
        }
    }

    fun floatPreference(
        @StringRes titleRes: Int,
        @StringRes summaryRes: Int,
        key: String,
        currentValue: Float,
        defaultValue: Float,
        minValue: Float,
        maxValue: Float,
        breadcrumb: String?,
        onValueSelected: (Float) -> Unit,
    ): Preference {
        return Preference(context).apply {
            title = getString(titleRes)
            this.key = key
            updateFloatPreferenceSummary(
                breadcrumb = breadcrumb,
                summaryRes = summaryRes,
                value = currentValue,
            )
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                showFloatPreferenceDialog(
                    titleRes = titleRes,
                    currentValue = currentValue,
                    defaultValue = defaultValue,
                    minValue = minValue,
                    maxValue = maxValue,
                ) { value ->
                    onValueSelected(value)
                    updateFloatPreferenceSummary(
                        breadcrumb = breadcrumb,
                        summaryRes = summaryRes,
                        value = value,
                    )
                }
                true
            }
        }
    }

    private fun showActionPreferenceDialog(
        title: String,
        actions: List<ConfigurableAction>,
        currentValue: String,
        resetValue: String,
        onActionSelected: (String) -> Unit,
    ) {
        val checkedIndex = actions.indexOfFirst { it.id == currentValue }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setSingleChoiceItems(actions.toEntryTitles(), checkedIndex) { dialog, which ->
                onActionSelected(actions[which].id)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.reset, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                onActionSelected(resetValue)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showFloatPreferenceDialog(
        @StringRes titleRes: Int,
        currentValue: Float,
        defaultValue: Float,
        minValue: Float,
        maxValue: Float,
        onValueSelected: (Float) -> Unit,
    ) {
        val min = minValue.roundToInt()
        val max = maxValue.roundToInt()
        val valueText = TextView(context).apply {
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }
        val seekBar = SeekBar(context).apply {
            this.max = max - min
            progress = currentValue.roundToInt().coerceIn(min, max) - min
        }

        fun selectedValue() = min + seekBar.progress

        fun updateValueText() {
            valueText.text = selectedValue().toString()
        }

        updateValueText()
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateValueText()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), 0)
            addView(
                valueText,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                seekBar,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(titleRes)
            .setView(layout)
            .setPositiveButton(R.string.apply, null)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.reset, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                onValueSelected(selectedValue().toFloat())
                dialog.dismiss()
            }
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                onValueSelected(defaultValue)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun getString(@StringRes stringRes: Int): String {
        return fragment.getString(stringRes)
    }

    private fun List<ConfigurableAction>.toEntryTitles(): Array<String> {
        return map { getString(it.titleRes) }.toTypedArray()
    }

    private fun Preference.updateActionSummary(actionId: String, breadcrumb: String?) {
        summary = formatSummary(
            breadcrumb = breadcrumb,
            detail = getString(ConfigurableAction.fromId(actionId).titleRes),
        )
    }

    private fun Preference.updateFloatPreferenceSummary(
        breadcrumb: String?,
        @StringRes summaryRes: Int,
        value: Float,
    ) {
        val currentValue = context.getString(
            R.string.settings_current_value,
            value.roundToInt(),
        )
        val detail = "${getString(summaryRes)}\n$currentValue"
        summary = formatSummary(breadcrumb = breadcrumb, detail = detail)
    }

    private fun formatSummary(breadcrumb: String?, detail: String?): String? {
        return when {
            breadcrumb.isNullOrBlank() -> detail
            detail.isNullOrBlank() -> breadcrumb
            else -> "$breadcrumb: $detail"
        }
    }

    private fun dp(value: Int): Int {
        return (value * fragment.resources.displayMetrics.density).roundToInt()
    }

    private class ThemeChoicePreference(
        context: android.content.Context,
        private val titleText: String,
        initialSelectedMode: String,
        private val onModeSelected: (String) -> Unit,
    ) : Preference(context) {
        private var selectedMode = initialSelectedMode

        init {
            layoutResource = R.layout.preference_theme_choice
            title = titleText
            isSelectable = false
            isIconSpaceReserved = false
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            (holder.findViewById(R.id.theme_choice_title) as TextView).text = titleText

            val group = holder.findViewById(R.id.theme_choice_group) as MaterialButtonToggleGroup
            group.clearOnButtonCheckedListeners()
            group.check(selectedMode.toButtonId())
            styleSegments(group)
            group.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                selectedMode = checkedId.toThemeMode()
                styleSegments(group)
                onModeSelected(selectedMode)
            }
        }

        private fun styleSegments(group: MaterialButtonToggleGroup) {
            SegmentedButtonStyler.style(group)
        }

        private fun String.toButtonId(): Int {
            return when (this) {
                Preferences.themeDark -> R.id.theme_choice_dark
                Preferences.themeLight -> R.id.theme_choice_light
                else -> R.id.theme_choice_system
            }
        }

        private fun Int.toThemeMode(): String {
            return when (this) {
                R.id.theme_choice_dark -> Preferences.themeDark
                R.id.theme_choice_light -> Preferences.themeLight
                else -> Preferences.themeSystem
            }
        }
    }
}
