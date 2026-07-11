// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.databinding.PageHomeFoldersBinding
import com.gitlab.mudlej.MjPdfReader.databinding.PageHomeLibraryBinding
import com.gitlab.mudlej.MjPdfReader.databinding.PageHomeRecentBinding

class HomeTabsAdapter(
    private val onPageAttached: (HomeTab, RecyclerView) -> Unit,
) : RecyclerView.Adapter<HomeTabsAdapter.PageViewHolder>() {

    class PageViewHolder(val recyclerView: RecyclerView) : RecyclerView.ViewHolder(recyclerView)

    override fun getItemCount() = HomeTab.entries.size

    override fun getItemViewType(position: Int) = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val recyclerView = when (HomeTab.entries[viewType]) {
            HomeTab.RECENT -> PageHomeRecentBinding.inflate(inflater, parent, false).pageRecyclerView
            HomeTab.LIBRARY -> PageHomeLibraryBinding.inflate(inflater, parent, false).pageRecyclerView
            HomeTab.FOLDERS -> PageHomeFoldersBinding.inflate(inflater, parent, false).pageRecyclerView
        }
        return PageViewHolder(recyclerView)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        onPageAttached(HomeTab.entries[position], holder.recyclerView)
    }
}
