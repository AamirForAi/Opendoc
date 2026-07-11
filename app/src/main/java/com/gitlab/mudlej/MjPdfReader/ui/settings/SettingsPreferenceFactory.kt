// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import android.content.DialogInterface
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
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.core.ui.SegmentedButtonStyler
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    fun backupExportPreference(breadcrumb: String?): Preference {
        return Preference(context).apply {
            title = getString(R.string.backup_export_title)
            key = "backupExport"
            summary = formatSummary(breadcrumb, getString(R.string.backup_export_summary))
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                (fragment as? SettingsFragment)?.startBackupExport()
                true
            }
        }
    }

    fun backupImportPreference(breadcrumb: String?): Preference {
        return Preference(context).apply {
            title = getString(R.string.backup_import_title)
            key = "backupImport"
            summary = formatSummary(breadcrumb, getString(R.string.backup_import_summary))
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                (fragment as? SettingsFragment)?.startBackupImport()
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
