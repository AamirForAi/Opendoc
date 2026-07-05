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
import androidx.annotation.StringRes
import androidx.preference.Preference

internal class SettingEntry(
    val page: SettingsPage,
    @StringRes val titleRes: Int,
    @StringRes private val summaryRes: Int? = null,
    private val keywords: List<String> = emptyList(),
    private val preferenceBuilder: SettingsPreferenceFactory.(breadcrumb: String?) -> Preference,
) {
    fun createPreference(factory: SettingsPreferenceFactory, breadcrumb: String?): Preference {
        return preferenceBuilder.invoke(factory, breadcrumb)
    }

    fun matches(context: Context, query: String): Boolean {
        val terms = query.lowercase().split(" ").filter { it.isNotBlank() }
        val searchableText = buildList {
            add(context.getString(titleRes))
            add(context.getString(page.titleRes))
            summaryRes?.let { add(context.getString(it)) }
            addAll(keywords)
        }.joinToString(" ").lowercase()

        return terms.all { term -> searchableText.contains(term) }
    }
}
