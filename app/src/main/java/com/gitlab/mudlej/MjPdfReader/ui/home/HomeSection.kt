package com.gitlab.mudlej.MjPdfReader.ui.home

import androidx.annotation.StringRes

sealed class HomeSection {

    data object PermissionCard : HomeSection()

    data class Hero(val items: List<HomeItem>) : HomeSection()

    data class Recents(val items: List<HomeItem>) : HomeSection()

    data object Chips : HomeSection()

    data class EmptyState(
        @StringRes val titleRes: Int,
        @StringRes val messageRes: Int,
    ) : HomeSection()

    data class ScanProgressRow(val foundCount: Int) : HomeSection()
}
