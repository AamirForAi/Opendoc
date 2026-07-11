package com.gitlab.mudlej.MjPdfReader.ui.home

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.ItemHomeGridCellBinding
import com.gitlab.mudlej.MjPdfReader.databinding.ItemHomeListRowBinding
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.core.text.StringUtil.formatEnumToTitle
import kotlinx.coroutines.CoroutineScope

class LibraryAdapter(
    private val coverCache: CoverCache,
    private val scope: CoroutineScope,
    private val functions: HomeItemFunctions,
    private val selection: () -> Set<String> = { emptySet() },
) : ListAdapter<HomeItem, RecyclerView.ViewHolder>(HomeItemComparator()) {

    var viewMode: HomeViewMode = HomeViewMode.GRID
    var coverWidthPx: Int = DEFAULT_COVER_WIDTH_PX
    var showFileSize: Boolean = false

    override fun getItemViewType(position: Int): Int {
        return if (viewMode == HomeViewMode.GRID) TYPE_GRID else TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GRID) {
            GridViewHolder(ItemHomeGridCellBinding.inflate(inflater, parent, false))
        } else {
            ListViewHolder(ItemHomeListRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is GridViewHolder -> holder.bind(item)
            is ListViewHolder -> holder.bind(item)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.contains(SELECTION_PAYLOAD)) {
            val item = getItem(position)
            when (holder) {
                is GridViewHolder -> holder.applySelection(item)
                is ListViewHolder -> holder.applySelection(item)
            }
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    fun notifySelectionChanged() {
        notifyItemRangeChanged(0, itemCount, SELECTION_PAYLOAD)
    }

    private fun isSelected(item: HomeItem) = item.hash in selection()

    inner class GridViewHolder(
        private val binding: ItemHomeGridCellBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HomeItem) {
            binding.title.text = item.title

            if (item.progressPercent > 0) {
                binding.progress.visibility = View.VISIBLE
                binding.progress.progress = item.progressPercent
            } else {
                binding.progress.visibility = View.GONE
            }

            applySelection(item)
            coverCache.bind(binding.cover, item.coverKey, item.uri, coverWidthPx, scope)

            binding.coverCard.setOnClickListener { functions.onItemClicked(item) }
            binding.coverCard.setOnLongClickListener { functions.onItemLongClicked(item) }
        }

        fun applySelection(item: HomeItem) {
            binding.coverCard.isChecked = isSelected(item)
        }
    }

    inner class ListViewHolder(
        private val binding: ItemHomeListRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HomeItem) {
            binding.title.text = item.title
            binding.meta.text = buildMeta(item)

            applySelection(item)
            coverCache.bind(binding.cover, item.coverKey, item.uri, LIST_COVER_WIDTH_PX, scope)

            binding.listCard.setOnClickListener { functions.onItemClicked(item) }
            binding.listCard.setOnLongClickListener { functions.onItemLongClicked(item) }
            binding.optionsButton.setOnClickListener { functions.onItemOptionsClicked(item) }
        }

        fun applySelection(item: HomeItem) {
            binding.listCard.isChecked = isSelected(item)
        }

        private fun buildMeta(item: HomeItem): String {
            val resources = binding.root.resources
            val parts = mutableListOf<String>()
            if (item.readingStatus != ReadingStatus.UNSET) {
                parts.add(item.readingStatus.name.formatEnumToTitle())
            }
            if (item.length > 0) {
                if (item.pageNumber > 0) {
                    parts.add("${item.pageNumber + 1}/${item.length}")
                } else {
                    parts.add(
                        resources.getQuantityString(R.plurals.home_pages, item.length, item.length)
                    )
                }
            }
            if (item.progressPercent > 0) {
                parts.add("${item.progressPercent}%")
            }
            if (showFileSize && item.sizeBytes > 0) {
                parts.add(Formatter.formatShortFileSize(binding.root.context, item.sizeBytes))
            }
            return parts.joinToString(" · ")
        }
    }

    companion object {
        private const val TYPE_GRID = 0
        private const val TYPE_LIST = 1
        private const val DEFAULT_COVER_WIDTH_PX = 320
        private const val LIST_COVER_WIDTH_PX = 160
        private const val SELECTION_PAYLOAD = "selection"
    }
}

class HomeItemComparator : DiffUtil.ItemCallback<HomeItem>() {

    override fun areItemsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean {
        return oldItem.hash == newItem.hash
    }

    override fun areContentsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean {
        return oldItem == newItem
    }
}
