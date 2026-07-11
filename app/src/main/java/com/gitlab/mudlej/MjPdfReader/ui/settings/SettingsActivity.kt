/*
 *   MJ PDF
 *   Copyright (C) 2023 Mudlej
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *  --------------------------
 *  This code was previously licensed under
 *
 *  MIT License
 *
 *  Copyright (c) 2018 Gokul Swaminathan
 *  Copyright (c) 2023 Mudlej
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package com.gitlab.mudlej.MjPdfReader.ui.settings

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.ui.setupScreenChrome
import com.gitlab.mudlej.MjPdfReader.databinding.ActivitySettingsBinding
import com.gitlab.mudlej.MjPdfReader.util.tintIconsForChrome


class SettingsActivity : AppCompatActivity(), SettingsFragment.Navigation {

    private lateinit var binding: ActivitySettingsBinding
    private var currentPage: SettingsPage? = null
    private var searchQuery = ""
    private var searchItem: MenuItem? = null
    private var searchView: SearchView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupScreenChrome()

        currentPage = savedInstanceState?.getString(CURRENT_PAGE_KEY)?.let(SettingsPage::valueOf)
        searchQuery = savedInstanceState?.getString(SEARCH_QUERY_KEY).orEmpty()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    currentPage != null -> showRoot(reverse = true)
                    searchQuery.isNotBlank() -> clearSearch()
                    else -> finish()
                }
            }
        })

        currentPage?.let { showPage(it, animate = false) } ?: showRoot(animate = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(CURRENT_PAGE_KEY, currentPage?.name)
        outState.putString(SEARCH_QUERY_KEY, searchQuery)
        super.onSaveInstanceState(outState)
    }

    override fun onSettingsPageSelected(page: SettingsPage) {
        showPage(page)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings_menu, menu)
        menu.tintIconsForChrome(this)
        searchItem = menu.findItem(R.id.searchSettingsOption)
        configureSearch(searchItem)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val item = menu.findItem(R.id.searchSettingsOption)
        item.isVisible = currentPage == null
        if (currentPage == null && searchQuery.isNotBlank()) {
            item.expandActionView()
            (item.actionView as? SearchView)?.setQuery(searchQuery, false)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> if (currentPage == null) finish() else showRoot(reverse = true)
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun configureSearch(item: MenuItem?) {
        val view = item?.actionView as? SearchView ?: return
        searchView = view
        view.queryHint = getString(R.string.settings_search_hint)
        view.maxWidth = Int.MAX_VALUE
        view.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                searchQuery = newText
                currentRootFragment()?.setSearchQuery(newText)
                return true
            }
        })
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                if (searchQuery.isNotBlank()) {
                    searchQuery = ""
                    currentRootFragment()?.setSearchQuery("")
                }
                return true
            }
        })
        if (searchQuery.isNotBlank()) {
            item.expandActionView()
            view.setQuery(searchQuery, false)
        }
    }

    private fun showRoot(animate: Boolean = true, reverse: Boolean = false) {
        currentPage = null
        title = getString(R.string.settings)
        invalidateOptionsMenu()
        val transaction = supportFragmentManager.beginTransaction()
        if (animate && reverse) {
            transaction.setCustomAnimations(R.anim.settings_enter_from_left, R.anim.settings_exit_to_right)
        }
        transaction
            .replace(R.id.fragment_container_view, SettingsFragment.root(searchQuery), ROOT_FRAGMENT_TAG)
            .commit()
    }

    private fun showPage(page: SettingsPage, animate: Boolean = true) {
        currentPage = page
        title = getString(page.titleRes)
        searchItem?.collapseActionView()
        searchView?.clearFocus()
        invalidateOptionsMenu()
        val transaction = supportFragmentManager.beginTransaction()
        if (animate) {
            transaction.setCustomAnimations(R.anim.settings_enter_from_right, R.anim.settings_exit_to_left)
        }
        transaction
            .replace(R.id.fragment_container_view, SettingsFragment.page(page))
            .commit()
    }

    private fun clearSearch() {
        searchView?.setQuery("", false)
        searchItem?.collapseActionView()
        searchQuery = ""
        currentRootFragment()?.setSearchQuery("")
    }

    private fun currentRootFragment(): SettingsFragment? {
        return supportFragmentManager.findFragmentByTag(ROOT_FRAGMENT_TAG) as? SettingsFragment
    }

    private companion object {
        const val CURRENT_PAGE_KEY = "currentSettingsPage"
        const val SEARCH_QUERY_KEY = "settingsSearchQuery"
        const val ROOT_FRAGMENT_TAG = "rootSettingsFragment"
    }
}
