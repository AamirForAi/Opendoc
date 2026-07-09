package com.gitlab.mudlej.MjPdfReader.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.ItemHomeChipRowBinding
import com.gitlab.mudlej.MjPdfReader.databinding.ItemHomeEmptyStateBinding
import com.gitlab.mudlej.MjPdfReader.databinding.ItemHomeHeroSectionBinding
import com.gitlab.mudlej.MjPdfReader.databinding.ItemHomePermissionCardBinding
import com.gitlab.mudlej.MjPdfReader.databinding.ItemHomeRecentsSectionBinding
import com.gitlab.mudlej.MjPdfReader.databinding.ItemHomeScanProgressBinding
import com.gitlab.mudlej.MjPdfReader.enums.ListFilter
import com.gitlab.mudlej.MjPdfReader.manager.thumbnail.CoverCache
import com.google.android.material.carousel.CarouselLayoutManager
import com.google.android.material.carousel.CarouselSnapHelper
import com.google.android.material.carousel.HeroCarouselStrategy
import kotlinx.coroutines.CoroutineScope

class HomeSectionsAdapter(
    private val coverCache: CoverCache,
    private val scope: CoroutineScope,
    private val functions: HomeItemFunctions,
    private val onGrantAccessClicked: () -> Unit,
    private val selectedFilter: () -> ListFilter,
    private val onChipSelected: (ListFilter) -> Unit,
) : ListAdapter<HomeSection, RecyclerView.ViewHolder>(SectionComparator()) {

    private var coverEpoch = 0

    fun rebindCovers() {
        coverEpoch++
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HomeSection.PermissionCard -> TYPE_PERMISSION_CARD
            is HomeSection.Hero -> TYPE_HERO
            is HomeSection.Recents -> TYPE_RECENTS
            is HomeSection.Chips -> TYPE_CHIPS
            is HomeSection.EmptyState -> TYPE_EMPTY_STATE
            is HomeSection.ScanProgressRow -> TYPE_SCAN_PROGRESS
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_PERMISSION_CARD -> PermissionCardViewHolder(
                ItemHomePermissionCardBinding.inflate(inflater, parent, false)
            )
            TYPE_HERO -> HeroSectionViewHolder(
                ItemHomeHeroSectionBinding.inflate(inflater, parent, false)
            )
            TYPE_RECENTS -> RecentsSectionViewHolder(
                ItemHomeRecentsSectionBinding.inflate(inflater, parent, false)
            )
            TYPE_CHIPS -> ChipRowViewHolder(
                ItemHomeChipRowBinding.inflate(inflater, parent, false)
            )
            TYPE_SCAN_PROGRESS -> ScanProgressViewHolder(
                ItemHomeScanProgressBinding.inflate(inflater, parent, false)
            )
            else -> EmptyStateViewHolder(
                ItemHomeEmptyStateBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val section = getItem(position)) {
            is HomeSection.PermissionCard -> (holder as PermissionCardViewHolder).bind()
            is HomeSection.Hero -> (holder as HeroSectionViewHolder).bind(section)
            is HomeSection.Recents -> (holder as RecentsSectionViewHolder).bind(section)
            is HomeSection.Chips -> (holder as ChipRowViewHolder).bind()
            is HomeSection.ScanProgressRow -> (holder as ScanProgressViewHolder).bind(section)
            is HomeSection.EmptyState -> (holder as EmptyStateViewHolder).bind(section)
        }
    }

    inner class PermissionCardViewHolder(
        private val binding: ItemHomePermissionCardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.grantAccessButton.setOnClickListener { onGrantAccessClicked() }
        }
    }

    inner class HeroSectionViewHolder(
        binding: ItemHomeHeroSectionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val heroAdapter = HeroCarouselAdapter(coverCache, scope, functions)
        private var boundCoverEpoch = -1

        init {
            binding.heroRecyclerView.layoutManager = CarouselLayoutManager(HeroCarouselStrategy())
            binding.heroRecyclerView.adapter = heroAdapter
            CarouselSnapHelper().attachToRecyclerView(binding.heroRecyclerView)
        }

        fun bind(section: HomeSection.Hero) {
            heroAdapter.submitList(section.items)
            if (boundCoverEpoch != coverEpoch) {
                boundCoverEpoch = coverEpoch
                heroAdapter.notifyDataSetChanged()
            }
        }
    }

    inner class RecentsSectionViewHolder(
        binding: ItemHomeRecentsSectionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val recentsAdapter = RecentsCarouselAdapter(coverCache, scope, functions)
        private var boundCoverEpoch = -1

        init {
            binding.recentsRecyclerView.layoutManager = CarouselLayoutManager()
            binding.recentsRecyclerView.adapter = recentsAdapter
        }

        fun bind(section: HomeSection.Recents) {
            recentsAdapter.submitList(section.items)
            if (boundCoverEpoch != coverEpoch) {
                boundCoverEpoch = coverEpoch
                recentsAdapter.notifyDataSetChanged()
            }
        }
    }

    inner class ChipRowViewHolder(
        private val binding: ItemHomeChipRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var suppressChipCallback = false

        fun bind() {
            suppressChipCallback = true
            binding.chipGroup.check(chipIdFor(selectedFilter()))
            suppressChipCallback = false

            binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
                if (suppressChipCallback) {
                    return@setOnCheckedStateChangeListener
                }
                val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
                onChipSelected(filterFor(checkedId))
            }
        }

        private fun chipIdFor(filter: ListFilter): Int {
            return when (filter) {
                ListFilter.TO_READ -> R.id.chipToRead
                ListFilter.READING -> R.id.chipReading
                ListFilter.ON_HOLD -> R.id.chipOnHold
                ListFilter.COMPLETED -> R.id.chipCompleted
                ListFilter.ABANDONED -> R.id.chipAbandoned
                else -> R.id.chipAllFiles
            }
        }

        private fun filterFor(chipId: Int): ListFilter {
            return when (chipId) {
                R.id.chipToRead -> ListFilter.TO_READ
                R.id.chipReading -> ListFilter.READING
                R.id.chipOnHold -> ListFilter.ON_HOLD
                R.id.chipCompleted -> ListFilter.COMPLETED
                R.id.chipAbandoned -> ListFilter.ABANDONED
                else -> ListFilter.ALL
            }
        }
    }

    class ScanProgressViewHolder(
        private val binding: ItemHomeScanProgressBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(section: HomeSection.ScanProgressRow) {
            binding.scanLabel.text = binding.root.context.getString(
                R.string.home_scanning_progress, section.foundCount
            )
        }
    }

    class EmptyStateViewHolder(
        private val binding: ItemHomeEmptyStateBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(section: HomeSection.EmptyState) {
            binding.emptyTitle.setText(section.titleRes)
            binding.emptyMessage.setText(section.messageRes)
        }
    }

    class SectionComparator : DiffUtil.ItemCallback<HomeSection>() {

        override fun areItemsTheSame(oldItem: HomeSection, newItem: HomeSection): Boolean {
            return oldItem::class == newItem::class
        }

        override fun areContentsTheSame(oldItem: HomeSection, newItem: HomeSection): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val TYPE_PERMISSION_CARD = 0
        private const val TYPE_HERO = 1
        private const val TYPE_RECENTS = 2
        private const val TYPE_CHIPS = 3
        private const val TYPE_EMPTY_STATE = 4
        private const val TYPE_SCAN_PROGRESS = 5
    }
}
