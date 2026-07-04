package com.gitlab.mudlej.MjPdfReader.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class ConfiguredAction(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val visible: Boolean = true,
    val run: () -> Unit,
)
