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

import android.content.DialogInterface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.enums.ConfigurableAction
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

    fun appThemePreference(breadcrumb: String?): Preference {
        return switchPreference(
            titleRes = R.string.dark_theme_for_app,
            key = Preferences.appFollowSystemThemeKey,
            defaultValue = Preferences.appFollowSystemThemeDefault,
            summaryRes = R.string.app_dark_theme_summary,
            breadcrumb = breadcrumb,
        ).apply {
            setOnPreferenceClickListener {
                if (!isChecked) {
                    setDefaultNightMode(MODE_NIGHT_NO)
                    return@setOnPreferenceClickListener true
                }

                MaterialAlertDialogBuilder(context)
                    .setTitle(getString(R.string.caution))
                    .setMessage(getString(R.string.app_dark_dialog_message))
                    .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                        dialog.dismiss()
                        setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM)
                    }
                    .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                        isChecked = false
                    }
                    .create()
                    .show()
                true
            }
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
            updateFullScreenButtonsSummary(breadcrumb)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                showFullScreenButtonsPreferenceDialog(context, appPreferences) {
                    updateFullScreenButtonsSummary(breadcrumb)
                }
                true
            }
        }
    }

    fun shortcutBarButtonsPreference(breadcrumb: String?): Preference {
        return Preference(context).apply {
            title = getString(R.string.shortcut_bar_buttons)
            key = Preferences.shortcutBarActionsKey
            updateShortcutBarButtonsSummary(breadcrumb)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                showShortcutBarButtonsPreferenceDialog(context, appPreferences) {
                    updateShortcutBarButtonsSummary(breadcrumb)
                }
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

    private fun Preference.updateFullScreenButtonsSummary(breadcrumb: String?) {
        summary = formatSummary(
            breadcrumb = breadcrumb,
            detail = fullScreenButtonsSummary(context, appPreferences),
        )
    }

    private fun Preference.updateShortcutBarButtonsSummary(breadcrumb: String?) {
        summary = formatSummary(
            breadcrumb = breadcrumb,
            detail = shortcutBarButtonsSummary(context, appPreferences),
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
}
