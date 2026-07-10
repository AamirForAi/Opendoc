package com.gitlab.mudlej.MjPdfReader.ui.bookmark

import android.annotation.SuppressLint
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.RowUserBookmarkBinding
import com.gitlab.mudlej.MjPdfReader.repository.UserBookmark
import com.gitlab.mudlej.MjPdfReader.ui.toc.TocPathResolver
import java.time.ZoneId

class UserBookmarkAdapter(
    private val onClick: (UserBookmark) -> Unit,
    private val onDelete: (UserBookmark) -> Unit,
    private val onRename: (UserBookmark) -> Unit,
) : RecyclerView.Adapter<UserBookmarkAdapter.UserBookmarkViewHolder>() {

    private val bookmarks = mutableListOf<UserBookmark>()
    var onDragRequested: ((RecyclerView.ViewHolder) -> Unit)? = null

    @SuppressLint("NotifyDataSetChanged")
    var tocPathResolver: TocPathResolver = TocPathResolver.EMPTY
        set(value) {
            field = value
            notifyDataSetChanged()
        }

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
            val customLabel = bookmark.label?.takeIf { it.isNotBlank() }
            val tocPath = tocPathResolver.resolve(bookmark.pageIndex)
            val relativeTime = DateUtils.getRelativeTimeSpanString(
                bookmark.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )
            val pageInfo = "$pageLabel · $relativeTime"
            binding.userBookmarkTopRow.visibility = View.GONE
            binding.userBookmarkSubtitle.visibility = View.VISIBLE
            binding.userBookmarkPageInfo.visibility = View.GONE
            binding.userBookmarkPageInfo.text = pageInfo
            binding.userBookmarkTitle.maxLines = if (customLabel == null && tocPath != null) 3 else 1
            binding.userBookmarkSubtitle.maxLines = if (customLabel != null && tocPath != null) 3 else 1
            when {
                customLabel != null && tocPath != null -> {
                    binding.userBookmarkTitle.text = customLabel
                    binding.userBookmarkSubtitle.text = tocPath
                    binding.userBookmarkPageInfo.visibility = View.VISIBLE
                }
                customLabel != null -> {
                    binding.userBookmarkTitle.text = customLabel
                    binding.userBookmarkSubtitle.text = pageInfo
                }
                tocPath != null -> {
                    binding.userBookmarkTopRow.visibility = View.VISIBLE
                    binding.userBookmarkTopPage.text = pageLabel
                    binding.userBookmarkTopTime.text = relativeTime
                    binding.userBookmarkTitle.text = tocPath
                    binding.userBookmarkSubtitle.visibility = View.GONE
                }
                else -> {
                    binding.userBookmarkTitle.text = pageLabel
                    binding.userBookmarkSubtitle.text = relativeTime
                }
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
