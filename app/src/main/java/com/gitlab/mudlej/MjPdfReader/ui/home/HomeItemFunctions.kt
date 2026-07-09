package com.gitlab.mudlej.MjPdfReader.ui.home

interface HomeItemFunctions {

    fun onItemClicked(item: HomeItem)

    fun onItemLongClicked(item: HomeItem): Boolean

    fun onItemOptionsClicked(item: HomeItem)
}
