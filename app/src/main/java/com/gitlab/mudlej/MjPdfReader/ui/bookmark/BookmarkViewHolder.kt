package com.gitlab.mudlej.MjPdfReader.ui.bookmark

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.data.Bookmark
import com.gitlab.mudlej.MjPdfReader.databinding.BookmarksListItemBinding

class BookmarkViewHolder(
    private val binding: BookmarksListItemBinding,
    private val bookmarkAdapter: BookmarkAdapter,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(bookmark: Bookmark) {
        val view = bookmarkAdapter.rootViewFor(bookmark, binding.root)
        (view.parent as? ViewGroup)?.removeView(view)
        binding.root.removeAllViews()
        binding.root.addView(view)
    }
}
