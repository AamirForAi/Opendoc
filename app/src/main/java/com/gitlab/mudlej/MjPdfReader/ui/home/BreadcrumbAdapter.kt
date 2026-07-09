package com.gitlab.mudlej.MjPdfReader.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.databinding.ItemHomeBreadcrumbBinding
import com.google.android.material.chip.Chip

class BreadcrumbAdapter(
    private val onCrumbClicked: (String?) -> Unit,
) : RecyclerView.Adapter<BreadcrumbAdapter.BreadcrumbViewHolder>() {

    private var crumbs: List<Crumb> = emptyList()

    fun submit(newCrumbs: List<Crumb>) {
        crumbs = newCrumbs
        notifyDataSetChanged()
    }

    override fun getItemCount() = if (crumbs.isEmpty()) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BreadcrumbViewHolder {
        val binding = ItemHomeBreadcrumbBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BreadcrumbViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BreadcrumbViewHolder, position: Int) {
        holder.bind(crumbs)
    }

    inner class BreadcrumbViewHolder(
        private val binding: ItemHomeBreadcrumbBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(crumbs: List<Crumb>) {
            binding.breadcrumbGroup.removeAllViews()
            crumbs.forEachIndexed { index, crumb ->
                val chip = Chip(binding.root.context)
                chip.text = crumb.label
                chip.isClickable = index < crumbs.lastIndex
                chip.isCheckable = false
                if (index < crumbs.lastIndex) {
                    chip.setOnClickListener { onCrumbClicked(crumb.path) }
                }
                binding.breadcrumbGroup.addView(chip)
            }
        }
    }
}
