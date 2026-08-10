// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.about

import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.ui.setupScreenChrome
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityWhatsNewBinding
import com.gitlab.mudlej.MjPdfReader.databinding.WhatsNewRowItemBinding
import com.gitlab.mudlej.MjPdfReader.databinding.WhatsNewSectionBinding

class WhatsNewActivity : AppCompatActivity() {

    private data class Change(
        @DrawableRes val iconRes: Int,
        @StringRes val titleRes: Int,
        @StringRes val bodyRes: Int,
    )

    private data class Section(
        @StringRes val titleRes: Int,
        val changes: List<Change>,
    )

    private lateinit var binding: ActivityWhatsNewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWhatsNewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupScreenChrome()
        binding.versionChip.text = "Version ${BuildConfig.VERSION_NAME}"
        bindSections()
    }

    private fun bindSections() {
        for (section in sections()) {
            val sectionBinding =
                WhatsNewSectionBinding.inflate(layoutInflater, binding.sectionsContainer, true)
            sectionBinding.sectionTitle.setText(section.titleRes)
            for (change in section.changes) {
                val rowBinding =
                    WhatsNewRowItemBinding.inflate(layoutInflater, sectionBinding.sectionRows, true)
                rowBinding.rowIcon.setImageResource(change.iconRes)
                rowBinding.rowTitle.setText(change.titleRes)
                rowBinding.rowBody.setText(change.bodyRes)
            }
        }
    }

    private fun sections(): List<Section> = listOf(
        Section(
            R.string.whats_new_section_reading,
            listOf(
                Change(R.drawable.ic_dual_page, R.string.whats_new_single_page_title, R.string.whats_new_single_page_body),
                Change(R.drawable.ic_fullscreen_grey, R.string.whats_new_fit_policy_title, R.string.whats_new_fit_policy_body),
                Change(R.drawable.ic_settings, R.string.whats_new_hide_delay_title, R.string.whats_new_hide_delay_body),
                Change(R.drawable.ic_dark_mode, R.string.whats_new_theme_toggle_title, R.string.whats_new_theme_toggle_body),
            ),
        ),
        Section(
            R.string.whats_new_section_search,
            listOf(
                Change(R.drawable.search_icon, R.string.whats_new_inline_search_title, R.string.whats_new_inline_search_body),
                Change(R.drawable.ic_history, R.string.whats_new_search_wrap_title, R.string.whats_new_search_wrap_body),
            ),
        ),
        Section(
            R.string.whats_new_section_saving,
            listOf(
                Change(R.drawable.ic_save, R.string.whats_new_safer_saving_title, R.string.whats_new_safer_saving_body),
                Change(R.drawable.ic_copy, R.string.whats_new_shared_copies_title, R.string.whats_new_shared_copies_body),
                Change(R.drawable.ic_folder, R.string.whats_new_library_records_title, R.string.whats_new_library_records_body),
            ),
        ),
        Section(
            R.string.whats_new_section_performance,
            listOf(
                Change(R.drawable.info_icon, R.string.whats_new_fixes_title, R.string.whats_new_fixes_body),
                Change(R.drawable.ic_translate, R.string.whats_new_translations_title, R.string.whats_new_translations_body),
            ),
        ),
    )

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
