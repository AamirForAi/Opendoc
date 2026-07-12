// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.usernotes

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.io.convertDateString
import com.gitlab.mudlej.MjPdfReader.databinding.RowUserNoteBinding
import com.gitlab.mudlej.MjPdfReader.pdf.SweptHighlight
import com.gitlab.mudlej.MjPdfReader.ui.tableofcontents.TableOfContentsPathResolver
import com.google.android.material.color.MaterialColors

class UserNoteAdapter(
    private val onClick: (SweptHighlight) -> Unit,
) : ListAdapter<SweptHighlight, UserNoteAdapter.UserNoteViewHolder>(SweptHighlightComparator) {

    @SuppressLint("NotifyDataSetChanged")
    var tableOfContentsPathResolver: TableOfContentsPathResolver = TableOfContentsPathResolver.EMPTY
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserNoteViewHolder {
        val binding = RowUserNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserNoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserNoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UserNoteViewHolder(
        private val binding: RowUserNoteBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SweptHighlight) {
            val context = binding.root.context
            val dotOutline = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOutline)
            binding.noteColorDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(item.color)
                setStroke(1, dotOutline)
            }

            val quote = item.quotedText.trim()
            binding.noteQuote.visibility = if (quote.isBlank()) View.GONE else View.VISIBLE
            binding.noteQuote.text = "“$quote”"

            binding.noteText.visibility = if (item.note.isNotBlank()) View.VISIBLE else View.GONE
            binding.noteText.text = item.note

            val infoParts = mutableListOf(context.getString(R.string.bookmark_page_label, item.pageIndex + 1))
            tableOfContentsPathResolver.resolve(item.pageIndex)?.let(infoParts::add)
            convertDateString(item.creationDate)?.let(infoParts::add)
            binding.noteInfo.text = infoParts.joinToString(" · ")

            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object SweptHighlightComparator : DiffUtil.ItemCallback<SweptHighlight>() {
        override fun areItemsTheSame(oldItem: SweptHighlight, newItem: SweptHighlight): Boolean {
            if (oldItem.pageIndex != newItem.pageIndex) {
                return false
            }
            return if (oldItem.groupKey.isEmpty() && newItem.groupKey.isEmpty()) {
                oldItem.annotationIndex == newItem.annotationIndex
            } else {
                oldItem.groupKey == newItem.groupKey
            }
        }

        override fun areContentsTheSame(oldItem: SweptHighlight, newItem: SweptHighlight): Boolean {
            return oldItem == newItem
        }
    }
}
