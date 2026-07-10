package com.gitlab.mudlej.MjPdfReader.ui.bookmark

import android.annotation.SuppressLint
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.RowUserBookmarkBinding
import com.gitlab.mudlej.MjPdfReader.repository.UserBookmark
import java.time.ZoneId

class UserBookmarkAdapter(
    private val onClick: (UserBookmark) -> Unit,
    private val onDelete: (UserBookmark) -> Unit,
    private val onRename: (UserBookmark) -> Unit,
) : RecyclerView.Adapter<UserBookmarkAdapter.UserBookmarkViewHolder>() {

    private val bookmarks = mutableListOf<UserBookmark>()
    var onDragRequested: ((RecyclerView.ViewHolder) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserBookmarkViewHolder {
        val binding = RowUserBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserBookmarkViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserBookmarkViewHolder, position: Int) {
        holder.bind(bookmarks[position])
    }

    override fun getItemCount() = bookmarks.size

    fun submitList(items: List<UserBookmark>) {
        bookmarks.clear()
        bookmarks.addAll(items)
        notifyDataSetChanged()
    }

    fun move(from: Int, to: Int): Boolean {
        if (from !in bookmarks.indices || to !in bookmarks.indices || from == to) {
            return false
        }
        bookmarks.add(to, bookmarks.removeAt(from))
        notifyItemMoved(from, to)
        return true
    }

    fun currentBookmarks(): List<UserBookmark> = bookmarks.toList()

    inner class UserBookmarkViewHolder(
        private val binding: RowUserBookmarkBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
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
            binding.userBookmarkRename.setOnClickListener { onRename(bookmark) }
            binding.userBookmarkDelete.setOnClickListener { onDelete(bookmark) }
            binding.userBookmarkDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onDragRequested?.invoke(this)
                }
                true
            }
        }
    }
}
