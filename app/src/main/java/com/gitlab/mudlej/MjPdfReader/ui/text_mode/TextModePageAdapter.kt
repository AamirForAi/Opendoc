package com.gitlab.mudlej.MjPdfReader.ui.text_mode

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.databinding.TextModePageItemBinding

class TextModePageAdapter(
    private val onRetry: (Int) -> Unit,
) : RecyclerView.Adapter<TextModePageViewHolder>() {

    private val pages = mutableListOf<TextModePageState>()
    private var settings = TextModeSettings()

    fun submitPageCount(pageCount: Int) {
        pages.clear()
        pages.addAll(List(pageCount) { pageIndex -> TextModePageState.NotLoaded(pageIndex) })
        notifyDataSetChanged()
    }

    fun pageState(pageIndex: Int): TextModePageState? {
        return pages.getOrNull(pageIndex)
    }

    fun updatePageState(state: TextModePageState) {
        if (state.pageIndex !in pages.indices) return

        pages[state.pageIndex] = state
        notifyItemChanged(state.pageIndex)
    }

    fun applySettings(settings: TextModeSettings) {
        this.settings = settings
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextModePageViewHolder {
        return TextModePageViewHolder(
            TextModePageItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onRetry,
        )
    }

    override fun onBindViewHolder(holder: TextModePageViewHolder, position: Int) {
        holder.bind(pages[position], settings)
    }

    override fun getItemCount() = pages.size
}
