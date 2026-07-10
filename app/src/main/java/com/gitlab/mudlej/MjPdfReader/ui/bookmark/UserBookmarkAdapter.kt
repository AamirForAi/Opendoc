package com.gitlab.mudlej.MjPdfReader.ui.bookmark

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.RowUserBookmarkBinding
import com.gitlab.mudlej.MjPdfReader.repository.UserBookmark
import java.time.ZoneId

class UserBookmarkAdapter(
    private val onClick: (UserBookmark) -> Unit,
    private val onDelete: (UserBookmark) -> Unit,
    private val onRename: (UserBookmark) -> Unit,
) : ListAdapter<UserBookmark, UserBookmarkAdapter.UserBookmarkViewHolder>(diffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserBookmarkViewHolder {
        val binding = RowUserBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserBookmarkViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserBookmarkViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UserBookmarkViewHolder(
        private val binding: RowUserBookmarkBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(bookmark: UserBookmark) {
            val context = binding.root.context
            val pageLabel = context.getString(R.string.bookmark_page_label, bookmark.pageIndex + 1)
            val relativeTime = DateUtils.getRelativeTimeSpanString(
                bookmark.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )
            binding.userBookmarkTitle.text = bookmark.label?.takeIf { it.isNotBlank() } ?: pageLabel
            binding.userBookmarkSubtitle.text = if (bookmark.label.isNullOrBlank()) {
                relativeTime
            } else {
                "$pageLabel · $relativeTime"
            }
            binding.root.setOnClickListener { onClick(bookmark) }
            binding.root.setOnLongClickListener {
                onRename(bookmark)
                true
            }
            binding.userBookmarkDelete.setOnClickListener { onDelete(bookmark) }
        }
    }

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<UserBookmark>() {
            override fun areItemsTheSame(oldItem: UserBookmark, newItem: UserBookmark): Boolean {
                return oldItem.fileHash == newItem.fileHash && oldItem.pageIndex == newItem.pageIndex
            }

            override fun areContentsTheSame(oldItem: UserBookmark, newItem: UserBookmark): Boolean {
                return oldItem == newItem
            }
        }
    }
}
