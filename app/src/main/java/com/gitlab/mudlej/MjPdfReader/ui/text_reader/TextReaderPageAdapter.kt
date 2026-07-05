package com.gitlab.mudlej.MjPdfReader.ui.text_reader

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.databinding.TextReaderPageItemBinding

class TextReaderPageAdapter(
    private val onRetry: (Int) -> Unit,
) : RecyclerView.Adapter<TextReaderPageViewHolder>() {

    private val pages = mutableListOf<TextReaderPageState>()
    private var settings = TextReaderSettings()

    fun submitPageCount(pageCount: Int) {
        pages.clear()
        pages.addAll(List(pageCount) { pageIndex -> TextReaderPageState.NotLoaded(pageIndex) })
        notifyDataSetChanged()
    }

    fun pageState(pageIndex: Int): TextReaderPageState? {
        return pages.getOrNull(pageIndex)
    }

    fun updatePageState(state: TextReaderPageState) {
        if (state.pageIndex !in pages.indices) return

        pages[state.pageIndex] = state
        notifyItemChanged(state.pageIndex)
    }

    fun applySettings(settings: TextReaderSettings) {
        this.settings = settings
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextReaderPageViewHolder {
        return TextReaderPageViewHolder(
            TextReaderPageItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onRetry,
        )
    }

    override fun onBindViewHolder(holder: TextReaderPageViewHolder, position: Int) {
        holder.bind(pages[position], settings)
    }

    override fun getItemCount() = pages.size
}
