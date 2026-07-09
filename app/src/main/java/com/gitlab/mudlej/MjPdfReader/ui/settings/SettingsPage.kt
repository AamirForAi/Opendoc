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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R

enum class SettingsPage(
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    @DrawableRes val iconRes: Int,
) {
    HOME(
        R.string.settings_home,
        R.string.settings_home_summary,
        R.drawable.ic_grid_view,
    ),
    APPEARANCE(
        R.string.settings_appearance,
        R.string.settings_appearance_summary,
        R.drawable.ic_color_palate,
    ),
    READING(
        R.string.settings_reading,
        R.string.settings_reading_summary,
        R.drawable.ic_book_bookmark,
    ),
    CUSTOMIZE_CONTROLS(
        R.string.customize_controls,
        R.string.customize_controls_summary,
        R.drawable.ic_display_settings,
    ),
    TEXT(
        R.string.settings_text,
        R.string.settings_text_summary,
        R.drawable.ic_text,
    ),
    HIGHLIGHTING(
        R.string.settings_highlighting,
        R.string.settings_highlighting_summary,
        R.drawable.ic_highlight,
    ),
    ADVANCED(
        R.string.advanced,
        R.string.advanced_summary,
        R.drawable.ic_settings,
    ),
}
