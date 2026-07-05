package com.gitlab.mudlej.MjPdfReader.ui.bookmark

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.get
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Bookmark
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.databinding.BookmarksListItemBinding
import com.google.android.material.card.MaterialCardView

class BookmarkViewHolder(
    private val binding: BookmarksListItemBinding,
    private val bookmarkFunctions: BookmarkFunctions,
    private val bookmarkAdapter: BookmarkAdapter,
    private val activity: BookmarksActivity
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(bookmark: Bookmark) {
        binding.root.removeAllViews()
        binding.root.addView(createSubBookmarkLayout(bookmark))
    }

    private fun createSubBookmarkLayout(subBookmark: Bookmark): MaterialCardView {

        val cardView = LayoutInflater.from(activity)
            .inflate(R.layout.children_bookmark_layout, null) as MaterialCardView

        val subBookmarkLayout = cardView[0] as ConstraintLayout
        val subToggleButton = subBookmarkLayout[0] as ImageView
        val subText = subBookmarkLayout[1] as TextView
        val subPageNumber = subBookmarkLayout[2] as TextView
        val subChildrenLayout = subBookmarkLayout[3] as LinearLayoutCompat

        subText.text = subBookmark.title
        subText.textSize = PDF.BOOKMARK_TEXT_SIZE - subBookmark.level * PDF.BOOKMARK_TEXT_SIZE_DEC

        subPageNumber.text = (subBookmark.pageIdx + 1).toString()
        subPageNumber.textSize = PDF.BOOKMARK_TEXT_SIZE - subBookmark.level * PDF.BOOKMARK_TEXT_SIZE_DEC

        subText.setOnClickListener { bookmarkFunctions.onBookmarkClicked(subBookmark) }
        subPageNumber.setOnClickListener { bookmarkFunctions.onBookmarkClicked(subBookmark) }
        subBookmarkLayout.setOnClickListener { bookmarkFunctions.onBookmarkClicked(subBookmark) }

//            if (subBookmark.level != 0)
//                subBookmarkLayout.setBackgroundResource(R.drawable.transparent_background)

        val visibleChildren = bookmarkAdapter.visibleChildren(subBookmark)
        if (visibleChildren.isNotEmpty()) {
            subChildrenLayout.removeAllViews()
            for (child in visibleChildren) {
                val layout = createSubBookmarkLayout(child)
                subChildrenLayout.addView(layout)
            }

            setExpansionState(subChildrenLayout, subToggleButton, bookmarkAdapter.isExpanded(subBookmark))

            if (!bookmarkAdapter.isFiltering()) {
                subToggleButton.setOnClickListener {
                    val expanded = bookmarkAdapter.toggleExpanded(subBookmark)
                    setExpansionState(subChildrenLayout, subToggleButton, expanded)
                }
            }
        }
        else {
            subToggleButton.setImageResource(R.drawable.ic_bullet_point)
        }
        return cardView
    }

    private fun setExpansionState(
        childrenLayout: LinearLayoutCompat,
        toggleButton: ImageView,
        expanded: Boolean
    ) {
        childrenLayout.visibility = if (expanded) View.VISIBLE else View.GONE
        toggleButton.setImageResource(
            if (expanded) R.drawable.ic_small_arrow_down else R.drawable.ic_small_arrow_right
        )
    }
}
