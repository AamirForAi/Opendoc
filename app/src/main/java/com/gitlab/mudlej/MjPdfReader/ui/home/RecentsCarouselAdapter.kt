package com.gitlab.mudlej.MjPdfReader.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.databinding.ItemHomeRecentBookBinding
import com.gitlab.mudlej.MjPdfReader.manager.thumbnail.CoverCache
import kotlinx.coroutines.CoroutineScope

class RecentsCarouselAdapter(
    private val coverCache: CoverCache,
    private val scope: CoroutineScope,
    private val functions: HomeItemFunctions,
) : ListAdapter<HomeItem, RecentsCarouselAdapter.RecentViewHolder>(HomeItemComparator()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentViewHolder {
        val binding = ItemHomeRecentBookBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecentViewHolder(
        private val binding: ItemHomeRecentBookBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HomeItem) {
            if (item.progressPercent > 0) {
                binding.progress.visibility = View.VISIBLE
                binding.progress.progress = item.progressPercent
            } else {
                binding.progress.visibility = View.GONE
            }

            val coverWidthPx = (COVER_WIDTH_DP * binding.root.resources.displayMetrics.density).toInt()
            coverCache.bind(binding.cover, item.coverKey, item.uri, coverWidthPx, scope)

            binding.recentCard.setOnClickListener { functions.onItemClicked(item) }
            binding.recentCard.setOnLongClickListener { functions.onItemLongClicked(item) }
        }
    }

    companion object {
        private const val COVER_WIDTH_DP = 110
    }
}
