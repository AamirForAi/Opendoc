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
            R.string.whats_new_section_home,
            listOf(
                Change(R.drawable.ic_home, R.string.whats_new_home_title, R.string.whats_new_home_body),
                Change(R.drawable.ic_folder, R.string.whats_new_folders_title, R.string.whats_new_folders_body),
                Change(R.drawable.search_icon, R.string.whats_new_search_title, R.string.whats_new_search_body),
                Change(R.drawable.ic_refresh, R.string.whats_new_scan_title, R.string.whats_new_scan_body),
                Change(R.drawable.ic_stats, R.string.whats_new_stats_title, R.string.whats_new_stats_body),
            ),
        ),
        Section(
            R.string.whats_new_section_reading,
            listOf(
                Change(R.drawable.ic_dual_page, R.string.whats_new_dual_page_title, R.string.whats_new_dual_page_body),
                Change(R.drawable.ic_reverse_direction, R.string.whats_new_rtl_title, R.string.whats_new_rtl_body),
                Change(R.drawable.ic_auto_scroll, R.string.whats_new_auto_scroll_title, R.string.whats_new_auto_scroll_body),
                Change(R.drawable.ic_text, R.string.whats_new_text_mode_title, R.string.whats_new_text_mode_body),
                Change(R.drawable.ic_crop_margins, R.string.whats_new_margins_title, R.string.whats_new_margins_body),
            ),
        ),
        Section(
            R.string.whats_new_section_annotation,
            listOf(
                Change(R.drawable.ic_highlight, R.string.whats_new_highlights_title, R.string.whats_new_highlights_body),
                Change(R.drawable.ic_signature, R.string.whats_new_signature_title, R.string.whats_new_signature_body),
                Change(R.drawable.ic_edit, R.string.whats_new_forms_title, R.string.whats_new_forms_body),
                Change(R.drawable.ic_translate, R.string.whats_new_translate_title, R.string.whats_new_translate_body),
                Change(R.drawable.ic_share, R.string.whats_new_quote_title, R.string.whats_new_quote_body),
                Change(R.drawable.ic_save, R.string.whats_new_saving_title, R.string.whats_new_saving_body),
            ),
        ),
        Section(
            R.string.whats_new_section_navigation,
            listOf(
                Change(R.drawable.ic_history, R.string.whats_new_nav_history_title, R.string.whats_new_nav_history_body),
                Change(R.drawable.ic_bookmarks, R.string.whats_new_bookmarks_title, R.string.whats_new_bookmarks_body),
                Change(R.drawable.ic_locate_me, R.string.whats_new_toc_title, R.string.whats_new_toc_body),
                Change(R.drawable.search_icon, R.string.whats_new_pdf_search_title, R.string.whats_new_pdf_search_body),
            ),
        ),
        Section(
            R.string.whats_new_section_privacy,
            listOf(
                Change(R.drawable.ic_incognito, R.string.whats_new_incognito_title, R.string.whats_new_incognito_body),
                Change(R.drawable.privacy_icon, R.string.whats_new_history_controls_title, R.string.whats_new_history_controls_body),
                Change(R.drawable.ic_copy, R.string.whats_new_backup_title, R.string.whats_new_backup_body),
            ),
        ),
        Section(
            R.string.whats_new_section_look,
            listOf(
                Change(R.drawable.ic_color_palate, R.string.whats_new_material_you_title, R.string.whats_new_material_you_body),
                Change(R.drawable.ic_settings, R.string.whats_new_settings_title, R.string.whats_new_settings_body),
                Change(R.drawable.ic_translate, R.string.whats_new_languages_title, R.string.whats_new_languages_body),
            ),
        ),
        Section(
            R.string.whats_new_section_performance,
            listOf(
                Change(R.drawable.ic_elevated_pdf, R.string.whats_new_rendering_title, R.string.whats_new_rendering_body),
                Change(R.drawable.info_icon, R.string.whats_new_stability_title, R.string.whats_new_stability_body),
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
