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

import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.enums.ConfigurableAction

internal class SettingsEntryProvider(private val preferences: Preferences) {

    fun entries(): List<SettingEntry> {
        return listOf(
            appearanceEntries(),
            readingEntries(),
            controlEntries(),
            textEntries(),
            advancedEntries(),
        ).flatten()
    }

    private fun appearanceEntries(): List<SettingEntry> {
        return listOf(
            appThemeEntry(),
            SettingEntry(
                page = SettingsPage.APPEARANCE,
                titleRes = R.string.dark_theme_for_pdf,
                keywords = listOf("pdf", "theme", "dark", "night", "system"),
            ) { breadcrumb ->
                pdfPagesThemePreference(breadcrumb = breadcrumb)
            },
            switchEntry(
                page = SettingsPage.APPEARANCE,
                titleRes = R.string.quality,
                key = Preferences.highQualityKey,
                defaultValue = Preferences.highQualityDefault,
                summaryRes = R.string.quality_summary,
                keywords = listOf("rendering", "quality"),
            ),
            switchEntry(
                page = SettingsPage.APPEARANCE,
                titleRes = R.string.anti_aliasing,
                key = Preferences.antiAliasingKey,
                defaultValue = Preferences.antiAliasingDefault,
                summaryRes = R.string.anti_aliasing_summary,
                keywords = listOf("rendering", "smooth"),
            ),
            switchEntry(
                page = SettingsPage.APPEARANCE,
                titleRes = R.string.space_between_pages,
                key = Preferences.spaceBetweenPagesKey,
                defaultValue = Preferences.spaceBetweenPagesDefault,
                keywords = listOf("spacing", "page gap"),
            ),
            switchEntry(
                page = SettingsPage.APPEARANCE,
                titleRes = R.string.always_hide_margins,
                key = Preferences.alwaysHideMarginsKey,
                defaultValue = Preferences.alwaysHideMarginsDefault,
                summaryRes = R.string.always_hide_margins_summary,
                keywords = listOf("crop", "margin", "margins"),
            ),
            switchEntry(
                page = SettingsPage.APPEARANCE,
                titleRes = R.string.show_scroll_handle_page_count_title,
                key = Preferences.showScrollHandlePageCountKey,
                defaultValue = Preferences.showScrollHandlePageCountDefault,
                summaryRes = R.string.show_scroll_handle_page_count_summary,
                keywords = listOf("scroll", "handle", "page count"),
            ),
            switchEntry(
                page = SettingsPage.APPEARANCE,
                titleRes = R.string.show_app_bar_page_count_title,
                key = Preferences.showAppBarPageCountKey,
                defaultValue = Preferences.showAppBarPageCountDefault,
                summaryRes = R.string.show_app_bar_page_count_summary,
                keywords = listOf("app bar", "toolbar", "title", "page count"),
            ),
        )
    }

    private fun readingEntries(): List<SettingEntry> {
        return listOf(
            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.horizontal_scrolling_mode,
                key = Preferences.horizontalScrollKey,
                defaultValue = Preferences.horizontalScrollDefault,
                summaryRes = R.string.horizontal_scrolling_summary,
                keywords = listOf("landscape", "swipe"),
            ),
            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.always_horizontal,
                key = Preferences.alwaysHorizontalKey,
                defaultValue = Preferences.alwaysHorizontalDefault,
                summaryRes = R.string.always_horizontal_summary,
                keywords = listOf("landscape", "orientation"),
            ),
            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.snap,
                key = Preferences.pageSnapKey,
                defaultValue = Preferences.pageSnapDefault,
                summaryRes = R.string.snap_summary,
                keywords = listOf("page", "scroll"),
            ),
            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.fling,
                key = Preferences.pageFlingKey,
                defaultValue = Preferences.pageFlingDefault,
                summaryRes = R.string.fling_summary,
                keywords = listOf("page", "scroll", "swipe"),
            ),
            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.turn_page_by_volume_buttons_title,
                key = Preferences.turnPageByVolumeButtonsKey,
                defaultValue = Preferences.turnPageByVolumeButtonsDefault,
                summaryRes = R.string.turn_page_by_volume_buttons_summary,
                keywords = listOf("volume", "buttons", "page turn"),
            ),
            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.keep_screen_on,
                key = Preferences.screenOnKey,
                defaultValue = Preferences.screenOnDefault,
                summaryRes = R.string.keep_screen_on_summary,
                keywords = listOf("display", "sleep", "screen"),
            ),
            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.double_tap_to_exit,
                key = Preferences.doubleTapToExitEnabledKey,
                defaultValue = Preferences.doubleTapToExitEnabledDefault,
                summaryRes = R.string.double_tap_to_exit_summary,
                keywords = listOf("exit", "back"),
            ),
        )
    }

    private fun controlEntries(): List<SettingEntry> {
        return listOf(
            actionEntry(
                page = SettingsPage.CUSTOMIZE_CONTROLS,
                titleRes = R.string.primary_button_action,
                key = Preferences.primaryButtonActionKey,
                defaultValue = Preferences.primaryButtonActionDefault,
                currentValue = preferences::getPrimaryButtonAction,
                actions = ConfigurableAction.toolbarActions,
                keywords = listOf("toolbar", "app bar", "button", "action"),
                onActionSelected = preferences::setPrimaryButtonAction,
            ),
            actionEntry(
                page = SettingsPage.CUSTOMIZE_CONTROLS,
                titleRes = R.string.secondary_button_action,
                key = Preferences.secondaryButtonActionKey,
                defaultValue = Preferences.secondaryButtonActionDefault,
                currentValue = preferences::getSecondaryButtonAction,
                actions = ConfigurableAction.toolbarActions,
                keywords = listOf("toolbar", "app bar", "button", "action"),
                onActionSelected = preferences::setSecondaryButtonAction,
            ),
            shortcutBarEntry(),
            fullScreenButtonsEntry(),
            switchEntry(
                page = SettingsPage.CUSTOMIZE_CONTROLS,
                titleRes = R.string.auto_full_screen,
                key = Preferences.autoFullScreenKey,
                defaultValue = Preferences.autoFullScreenDefault,
                summaryRes = R.string.auto_full_screen_summary,
                keywords = listOf("fullscreen", "startup", "open"),
            ),
            switchEntry(
                page = SettingsPage.CUSTOMIZE_CONTROLS,
                titleRes = R.string.fullscreen_info_show_time,
                key = Preferences.fullScreenInfoShowTimeKey,
                defaultValue = Preferences.fullScreenInfoShowTimeDefault,
                summaryRes = R.string.fullscreen_info_show_time_summary,
                keywords = listOf("fullscreen", "info", "clock"),
            ),
            switchEntry(
                page = SettingsPage.CUSTOMIZE_CONTROLS,
                titleRes = R.string.fullscreen_info_show_pdf_name,
                key = Preferences.fullScreenInfoShowPdfNameKey,
                defaultValue = Preferences.fullScreenInfoShowPdfNameDefault,
                summaryRes = R.string.fullscreen_info_show_pdf_name_summary,
                keywords = listOf("fullscreen", "info", "title", "file"),
            ),
            switchEntry(
                page = SettingsPage.CUSTOMIZE_CONTROLS,
                titleRes = R.string.fullscreen_info_show_page_number,
                key = Preferences.fullScreenInfoShowPageNumberKey,
                defaultValue = Preferences.fullScreenInfoShowPageNumberDefault,
                summaryRes = R.string.fullscreen_info_show_page_number_summary,
                keywords = listOf("fullscreen", "info", "page"),
            ),
            switchEntry(
                page = SettingsPage.CUSTOMIZE_CONTROLS,
                titleRes = R.string.fullscreen_info_show_reading_percentage,
                key = Preferences.fullScreenInfoShowReadingPercentageKey,
                defaultValue = Preferences.fullScreenInfoShowReadingPercentageDefault,
                summaryRes = R.string.fullscreen_info_show_reading_percentage_summary,
                keywords = listOf("fullscreen", "info", "percent", "progress"),
            ),
        )
    }

    private fun textEntries(): List<SettingEntry> {
        return listOf(
            switchEntry(
                page = SettingsPage.TEXT,
                titleRes = R.string.default_text_mode,
                key = Preferences.defaultTextModeKey,
                defaultValue = Preferences.defaultTextModeDefault,
                summaryRes = R.string.default_text_mode_summary,
                keywords = listOf("text", "extract", "mode"),
            ),
            switchEntry(
                page = SettingsPage.TEXT,
                titleRes = R.string.inline_text_selection_title,
                key = Preferences.inlineTextSelectionKey,
                defaultValue = Preferences.inlineTextSelectionDefault,
                summaryRes = R.string.inline_text_selection_summary,
                keywords = listOf("inline", "selection", "copy", "text"),
            ),
            switchEntry(
                page = SettingsPage.TEXT,
                titleRes = R.string.detect_existing_highlights_title,
                key = Preferences.detectExistingHighlightsKey,
                defaultValue = Preferences.detectExistingHighlightsDefault,
                summaryRes = R.string.detect_existing_highlights_summary,
                keywords = listOf("highlight", "annotation", "selection", "detect"),
            ),
            switchEntry(
                page = SettingsPage.TEXT,
                titleRes = R.string.search_ignore_accents_title,
                key = Preferences.searchIgnoreAccentsKey,
                defaultValue = Preferences.searchIgnoreAccentsDefault,
                summaryRes = R.string.search_ignore_accents_summary,
                keywords = listOf("search", "accents", "diacritics"),
            ),
        )
    }

    private fun advancedEntries(): List<SettingEntry> {
        return listOf(
            floatPreferenceEntry(
                page = SettingsPage.ADVANCED,
                titleRes = R.string.part_size,
                summaryRes = R.string.part_size_summary,
                key = Preferences.partSizeKey,
                currentValue = preferences::getPartSize,
                defaultValue = Preferences.partSizeDefault,
                minValue = Preferences.minPartSize,
                maxValue = Preferences.maxPartSize,
                keywords = listOf("advanced", "render", "cache", "part"),
                onValueSelected = preferences::setPartSize,
            ),
            floatPreferenceEntry(
                page = SettingsPage.ADVANCED,
                titleRes = R.string.max_zoom,
                summaryRes = R.string.max_zoom_summary,
                key = Preferences.maxZoomKey,
                currentValue = preferences::getMaxZoom,
                defaultValue = Preferences.maxZoomDefault,
                minValue = Preferences.minMaxZoom,
                maxValue = Preferences.maxMaxZoom,
                keywords = listOf("advanced", "zoom", "scale"),
                onValueSelected = preferences::setMaxZoom,
            ),
        )
    }

    private fun appThemeEntry(): SettingEntry {
        return SettingEntry(
            page = SettingsPage.APPEARANCE,
            titleRes = R.string.dark_theme_for_app,
            keywords = listOf("ui", "theme", "dark", "night", "system"),
        ) { breadcrumb ->
            interfaceThemePreference(breadcrumb = breadcrumb)
        }
    }

    private fun shortcutBarEntry(): SettingEntry {
        return SettingEntry(
            page = SettingsPage.CUSTOMIZE_CONTROLS,
            titleRes = R.string.shortcut_bar_buttons,
            summaryRes = R.string.shortcut_bar_buttons_summary,
            keywords = listOf("shortcut", "buttons", "actions"),
        ) { breadcrumb ->
            shortcutBarButtonsPreference(breadcrumb = breadcrumb)
        }
    }

    private fun fullScreenButtonsEntry(): SettingEntry {
        return SettingEntry(
            page = SettingsPage.CUSTOMIZE_CONTROLS,
            titleRes = R.string.fullscreen_buttons,
            summaryRes = R.string.fullscreen_buttons_summary,
            keywords = listOf("fullscreen", "buttons", "actions"),
        ) { breadcrumb ->
            fullScreenButtonsPreference(breadcrumb = breadcrumb)
        }
    }
}

private fun switchEntry(
    page: SettingsPage,
    @StringRes titleRes: Int,
    key: String,
    defaultValue: Boolean,
    @StringRes summaryRes: Int? = null,
    keywords: List<String> = emptyList(),
): SettingEntry {
    return SettingEntry(
        page = page,
        titleRes = titleRes,
        summaryRes = summaryRes,
        keywords = keywords,
    ) { breadcrumb ->
        switchPreference(
            titleRes = titleRes,
            key = key,
            defaultValue = defaultValue,
            summaryRes = summaryRes,
            breadcrumb = breadcrumb,
        )
    }
}

private fun actionEntry(
    page: SettingsPage,
    @StringRes titleRes: Int,
    key: String,
    defaultValue: String,
    currentValue: () -> String,
    actions: List<ConfigurableAction>,
    keywords: List<String>,
    onActionSelected: (String) -> Unit,
): SettingEntry {
    return SettingEntry(
        page = page,
        titleRes = titleRes,
        keywords = keywords,
    ) { breadcrumb ->
        actionPreference(
            titleRes = titleRes,
            key = key,
            defaultValue = defaultValue,
            currentValue = currentValue(),
            actions = actions,
            breadcrumb = breadcrumb,
            onActionSelected = onActionSelected,
        )
    }
}

private fun floatPreferenceEntry(
    page: SettingsPage,
    @StringRes titleRes: Int,
    @StringRes summaryRes: Int,
    key: String,
    currentValue: () -> Float,
    defaultValue: Float,
    minValue: Float,
    maxValue: Float,
    keywords: List<String>,
    onValueSelected: (Float) -> Unit,
): SettingEntry {
    return SettingEntry(
        page = page,
        titleRes = titleRes,
        summaryRes = summaryRes,
        keywords = keywords,
    ) { breadcrumb ->
        floatPreference(
            titleRes = titleRes,
            summaryRes = summaryRes,
            key = key,
            currentValue = currentValue(),
            defaultValue = defaultValue,
            minValue = minValue,
            maxValue = maxValue,
            breadcrumb = breadcrumb,
            onValueSelected = onValueSelected,
        )
    }
}
