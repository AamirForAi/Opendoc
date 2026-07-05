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
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences

class SettingsFragment : PreferenceFragmentCompat() {

    interface Navigation {
        fun onSettingsPageSelected(page: SettingsPage)
    }

    private var navigation: Navigation? = null
    private lateinit var preferenceFactory: SettingsPreferenceFactory
    private var searchQuery = ""

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

    companion object {
        private const val ARG_PAGE = "settingsPage"
        private const val ARG_SEARCH_QUERY = "settingsSearchQuery"

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
