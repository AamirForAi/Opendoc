// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction

internal class SettingsEntryProvider(private val preferences: Preferences) {

    fun entries(): List<SettingEntry> {
        return listOf(
            homeEntries(),
            appearanceEntries(),
            readingEntries(),
            controlEntries(),
            textEntries(),
            highlightingEntries(),
            privacyEntries(),
            advancedEntries(),
        ).flatten()
    }

    private fun homeEntries(): List<SettingEntry> {
        return listOf(
            switchEntry(
                page = SettingsPage.HOME,
                titleRes = R.string.home_show_pdf_title_title,
                key = Preferences.homeShowPdfTitleKey,
                defaultValue = Preferences.homeShowPdfTitleDefault,
                summaryRes = R.string.home_show_pdf_title_summary,
                keywords = listOf("home", "title", "metadata", "name"),
            ),
        )
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
                titleRes = R.string.two_pages_per_row_title,
                key = Preferences.twoPagesPerRowKey,
                defaultValue = Preferences.twoPagesPerRowDefault,
                summaryRes = R.string.two_pages_per_row_summary,
                keywords = listOf("two pages", "double", "spread", "facing", "side by side"),
            ),
            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.two_pages_first_page_alone_title,
                key = Preferences.twoPagesFirstPageAloneKey,
                defaultValue = Preferences.twoPagesFirstPageAloneDefault,
                summaryRes = R.string.two_pages_first_page_alone_summary,
                keywords = listOf("cover", "first page", "spread", "book"),
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
                titleRes = R.string.browser_scroll_mode_title,
                key = Preferences.browserScrollModeKey,
                defaultValue = Preferences.browserScrollModeDefault,
                summaryRes = R.string.browser_scroll_mode_summary,
                keywords = listOf("browser", "scroll", "pan", "lock", "diagonal"),
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
                titleRes = R.string.turn_page_by_mouse_buttons_title,
                key = Preferences.turnPageByMouseButtonsKey,
                defaultValue = Preferences.turnPageByMouseButtonsDefault,
                summaryRes = R.string.turn_page_by_mouse_buttons_summary,
                keywords = listOf("mouse", "buttons", "page turn", "back", "forward"),
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
            switchEntry(
                page = SettingsPage.READING,
                titleRes = R.string.always_open_first_page_title,
                key = Preferences.alwaysOpenAtFirstPageKey,
                defaultValue = Preferences.alwaysOpenAtFirstPageDefault,
                summaryRes = R.string.always_open_first_page_summary,
                keywords = listOf("first page", "page 1", "start", "resume", "position"),
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
            scrollingInfoCardEntry(),
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
                titleRes = R.string.search_ignore_accents_title,
                key = Preferences.searchIgnoreAccentsKey,
                defaultValue = Preferences.searchIgnoreAccentsDefault,
                summaryRes = R.string.search_ignore_accents_summary,
                keywords = listOf("search", "accents", "diacritics"),
            ),
        )
    }

    private fun highlightingEntries(): List<SettingEntry> {
        return listOf(
            SettingEntry(
                page = SettingsPage.HIGHLIGHTING,
                titleRes = R.string.highlight_colors,
                summaryRes = R.string.highlight_colors_summary,
                keywords = listOf("highlight", "color", "colors", "palette", "strip"),
            ) { breadcrumb ->
                highlightColorsPreference(breadcrumb = breadcrumb)
            },
            switchEntry(
                page = SettingsPage.HIGHLIGHTING,
                titleRes = R.string.detect_existing_highlights_title,
                key = Preferences.detectExistingHighlightsKey,
                defaultValue = Preferences.detectExistingHighlightsDefault,
                summaryRes = R.string.detect_existing_highlights_summary,
                keywords = listOf("highlight", "annotation", "selection", "detect"),
            ),
        )
    }

    private fun privacyEntries(): List<SettingEntry> {
        return listOf(
            switchEntry(
                page = SettingsPage.HISTORY_PRIVACY,
                titleRes = R.string.history_enabled_title,
                key = Preferences.historyEnabledKey,
                defaultValue = Preferences.historyEnabledDefault,
                summaryRes = R.string.history_enabled_summary,
                keywords = listOf("history", "privacy", "save", "recent", "remember", "positions"),
            ),
            SettingEntry(
                page = SettingsPage.HISTORY_PRIVACY,
                titleRes = R.string.open_in_incognito,
                summaryRes = R.string.open_in_incognito_summary,
                keywords = listOf("incognito", "private", "privacy", "history"),
            ) { breadcrumb ->
                openIncognitoPreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.HISTORY_PRIVACY,
                titleRes = R.string.reading_history,
                summaryRes = R.string.reading_history_row_summary,
                keywords = listOf("history", "recent", "view", "records", "privacy"),
            ) { breadcrumb ->
                readingHistoryPreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.HISTORY_PRIVACY,
                titleRes = R.string.clear_reading_history_title,
                summaryRes = R.string.clear_reading_history_summary,
                keywords = listOf("clear", "delete", "history", "recent", "positions", "privacy"),
            ) { breadcrumb ->
                clearReadingHistoryPreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.HISTORY_PRIVACY,
                titleRes = R.string.clear_saved_passwords_title,
                summaryRes = R.string.clear_saved_passwords_summary,
                keywords = listOf("clear", "delete", "passwords", "privacy"),
            ) { breadcrumb ->
                clearSavedPasswordsPreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.HISTORY_PRIVACY,
                titleRes = R.string.clear_bookmarks_title,
                summaryRes = R.string.clear_bookmarks_summary,
                keywords = listOf("clear", "delete", "bookmarks", "privacy"),
            ) { breadcrumb ->
                clearBookmarksPreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.HISTORY_PRIVACY,
                titleRes = R.string.clear_annotation_journals_title,
                summaryRes = R.string.clear_annotation_journals_summary,
                keywords = listOf("clear", "delete", "highlights", "signature", "recovery", "privacy"),
            ) { breadcrumb ->
                clearAnnotationJournalsPreference(breadcrumb)
            },
        )
    }

    private fun advancedEntries(): List<SettingEntry> {
        return listOf(
            switchEntry(
                page = SettingsPage.ADVANCED,
                titleRes = R.string.disable_home_library_title,
                key = Preferences.homeDisabledKey,
                defaultValue = Preferences.homeDisabledDefault,
                summaryRes = R.string.disable_home_library_summary,
                keywords = listOf("home", "library", "disable", "launch", "start", "picker"),
            ),
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
            SettingEntry(
                page = SettingsPage.ADVANCED,
                titleRes = R.string.backup_export_title,
                summaryRes = R.string.backup_export_summary,
                keywords = listOf("backup", "export", "save", "data", "transfer", "progress"),
            ) { breadcrumb ->
                backupExportPreference(breadcrumb)
            },
            SettingEntry(
                page = SettingsPage.ADVANCED,
                titleRes = R.string.backup_import_title,
                summaryRes = R.string.backup_import_summary,
                keywords = listOf("backup", "import", "restore", "data", "transfer", "progress"),
            ) { breadcrumb ->
                backupImportPreference(breadcrumb)
            },
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

    private fun scrollingInfoCardEntry(): SettingEntry {
        return SettingEntry(
            page = SettingsPage.CUSTOMIZE_CONTROLS,
            titleRes = R.string.scrolling_info_card,
            summaryRes = R.string.scrolling_info_card_summary,
            keywords = listOf("scrolling", "fullscreen", "info", "card", "time", "clock", "page", "percent", "progress", "title", "file"),
        ) { breadcrumb ->
            scrollingInfoCardPreference(breadcrumb = breadcrumb)
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
