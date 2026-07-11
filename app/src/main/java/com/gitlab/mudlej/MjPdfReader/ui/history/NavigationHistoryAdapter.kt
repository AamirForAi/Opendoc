package com.gitlab.mudlej.MjPdfReader.ui.history

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.RowNavigationHistoryBinding

class NavigationHistoryAdapter(
    private val onClick: (NavigationHistoryRow) -> Unit,
) : ListAdapter<NavigationHistoryRow, NavigationHistoryAdapter.NavigationHistoryViewHolder>(diffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NavigationHistoryViewHolder {
        val binding = RowNavigationHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NavigationHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NavigationHistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NavigationHistoryViewHolder(
        private val binding: RowNavigationHistoryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: NavigationHistoryRow) {
            val context = binding.root.context
            val relativeTime = DateUtils.getRelativeTimeSpanString(
                entry.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )
            binding.navigationHistoryTitle.text = context.getString(R.string.bookmark_page_label, entry.pageIndex + 1)
            binding.navigationHistoryTocPath.text = entry.tableOfContentsPath.orEmpty()
            binding.navigationHistoryTocPath.visibility = if (entry.tableOfContentsPath.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.navigationHistorySubtitle.text = context.getString(
                R.string.history_entry_subtitle_format,
                context.getString(entry.origin.labelRes),
                relativeTime,
            )
            binding.root.setOnClickListener { onClick(entry) }
        }
    }

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<NavigationHistoryRow>() {
            override fun areItemsTheSame(oldItem: NavigationHistoryRow, newItem: NavigationHistoryRow): Boolean {
                return oldItem.backStackIndex == newItem.backStackIndex
            }

            override fun areContentsTheSame(oldItem: NavigationHistoryRow, newItem: NavigationHistoryRow): Boolean {
                return oldItem == newItem
            }
        }
    }
}
