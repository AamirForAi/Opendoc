// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.ui

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar

object AppSnackbar {

    fun make(view: View, @StringRes resId: Int, duration: Int): Snackbar {
        return make(view, view.resources.getText(resId), duration)
    }

    fun make(view: View, text: CharSequence, duration: Int): Snackbar {
        return Snackbar.make(view, text, duration).also(::applyCardStyle)
    }

    private fun applyCardStyle(snackbar: Snackbar) {
        val view = snackbar.view
        val density = view.resources.displayMetrics.density
        val backgroundColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainerHigh)
        val outlineColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOutlineVariant)
        val textColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface)
        val actionColor = MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary)

        view.background = MaterialShapeDrawable(
            ShapeAppearanceModel.builder().setAllCornerSizes(16 * density).build()
        ).apply {
            fillColor = ColorStateList.valueOf(backgroundColor)
            strokeColor = ColorStateList.valueOf(outlineColor)
            strokeWidth = density
        }
        view.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        ViewCompat.setElevation(view, 6 * density)
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            val margin = (16 * density).toInt()
            params.setMargins(margin, margin, margin, margin)
            view.layoutParams = params
        }
        val extraVerticalPadding = (6 * density).toInt()
        view.setPadding(
            view.paddingLeft,
            view.paddingTop + extraVerticalPadding,
            view.paddingRight,
            view.paddingBottom + extraVerticalPadding,
        )
        snackbar.setTextColor(textColor)
        snackbar.setActionTextColor(actionColor)
    }
}
