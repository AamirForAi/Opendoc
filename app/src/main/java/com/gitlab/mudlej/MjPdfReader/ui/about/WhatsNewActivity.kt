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
                Change(
                    R.drawable.ic_home,
                    R.string.whats_new_home_title,
                    R.string.whats_new_home_body,
                ),
                Change(
                    R.drawable.ic_folder,
                    R.string.whats_new_folders_title,
                    R.string.whats_new_folders_body,
                ),
            ),
        ),
        Section(
            R.string.whats_new_section_reading,
            listOf(
                Change(
                    R.drawable.ic_book_bookmark,
                    R.string.whats_new_two_pages_title,
                    R.string.whats_new_two_pages_body,
                ),
            ),
        ),
        Section(
            R.string.whats_new_section_annotation,
            listOf(
                Change(
                    R.drawable.ic_highlight,
                    R.string.whats_new_highlights_title,
                    R.string.whats_new_highlights_body,
                ),
            ),
        ),
        Section(
            R.string.whats_new_section_privacy,
            listOf(
                Change(
                    R.drawable.ic_incognito,
                    R.string.whats_new_incognito_title,
                    R.string.whats_new_incognito_body,
                ),
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
