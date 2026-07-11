package com.gitlab.mudlej.MjPdfReader.ui.tableofcontents

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.databinding.TableOfContentsRowItemBinding

class TableOfContentsViewHolder(
    private val binding: TableOfContentsRowItemBinding,
    private val tableOfContentsAdapter: TableOfContentsAdapter,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(row: TableOfContentsRow) {
        val entry = row.entry
        val textSize = PDF.TABLE_OF_CONTENTS_TEXT_SIZE - entry.level * PDF.TABLE_OF_CONTENTS_TEXT_SIZE_DEC

        indent(entry.level)

        binding.bookmarkText.text = entry.title
        binding.bookmarkText.textSize = textSize
        binding.bookmarkPageNumber.text = (entry.pageIdx + 1).toString()
        binding.bookmarkPageNumber.textSize = textSize

        val onClick = View.OnClickListener { tableOfContentsAdapter.bookmarkFunctions.onEntryClicked(entry) }
        binding.root.setOnClickListener(onClick)
        binding.bookmarkText.setOnClickListener(onClick)
        binding.bookmarkPageNumber.setOnClickListener(onClick)

        bindToggle(row)
    }

    private fun bindToggle(row: TableOfContentsRow) {
        val toggle = binding.toggleButton
        if (!row.expandable) {
            toggle.setImageResource(R.drawable.ic_bullet_point)
            toggle.setOnClickListener(null)
            toggle.isClickable = false
            return
        }

        toggle.setImageResource(
            if (row.expanded) R.drawable.ic_small_arrow_down else R.drawable.ic_small_arrow_right
        )

        if (tableOfContentsAdapter.isFiltering()) {
            toggle.setOnClickListener(null)
            toggle.isClickable = false
        } else {
            toggle.setOnClickListener { tableOfContentsAdapter.onToggleClicked(row.entry) }
        }
    }

    private fun indent(level: Int) {
        val step = itemView.resources.getDimensionPixelSize(R.dimen.bookmark_indent_step)
        val baseMargin = itemView.resources.getDimensionPixelSize(R.dimen.bookmark_card_horizontal_margin)
        val params = binding.root.layoutParams as ViewGroup.MarginLayoutParams
        params.marginStart = baseMargin + level * step
        binding.root.layoutParams = params
    }
}
