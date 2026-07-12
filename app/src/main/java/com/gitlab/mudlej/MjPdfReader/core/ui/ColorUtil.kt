// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.ActionBar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gitlab.mudlej.MjPdfReader.R
import com.google.android.material.color.MaterialColors
import java.util.WeakHashMap


object ColorUtil {

    private const val STATUS_BAR_BACKGROUND_TAG = "mj_pdf_status_bar_background"
    private const val NAVIGATION_BAR_BACKGROUND_TAG = "mj_pdf_navigation_bar_background"

    private val windowBarsBackgrounds = WeakHashMap<Window, SystemBarsBackground>()

    fun colorize(context: Context, window: Window, actionBar: ActionBar?, topBarColor: Int? = null) {
        val color = getBarColor(context)
        val statusBarColor = topBarColor ?: color

        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = statusBarColor
        window.navigationBarColor = color
        showSystemBars(window)
        val barsBackground = applyWindowBarsBackground(context, window, statusBarColor, color)
        drawSystemBarBackgrounds(window, statusBarColor, color, barsBackground)
        setSystemBarIconColors(window, statusBarColor, color)
        fitContentBelowSystemBars(window)

        actionBar?.setBackgroundDrawable(ColorDrawable(statusBarColor))
        // Flatten the app bar so it blends into the status bar.
        actionBar?.elevation = 0f
    }

    fun enterFullscreen(window: Window) {
        setSystemBarBackgroundsVisible(window, false)
        setContentFitsSystemBars(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    fun exitFullscreen(context: Context, window: Window, actionBar: ActionBar?, topBarColor: Int? = null) {
        colorize(context, window, actionBar, topBarColor)
    }

    fun getBarColor(context: Context): Int {
        return MaterialColors.getColor(
            context,
            R.attr.colorSurfaceContainerHigh,
            0
        )
    }

    private fun setSystemBarIconColors(window: Window, statusBarColor: Int, navigationBarColor: Int) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = MaterialColors.isColorLight(statusBarColor)
        controller.isAppearanceLightNavigationBars = MaterialColors.isColorLight(navigationBarColor)
    }

    private fun applyWindowBarsBackground(
        context: Context,
        window: Window,
        statusBarColor: Int,
        navigationBarColor: Int,
    ): SystemBarsBackground {
        val background = windowBarsBackgrounds.getOrPut(window) { SystemBarsBackground() }
        background.baseColor = MaterialColors.getColor(
            context, android.R.attr.colorBackground, navigationBarColor
        )
        background.statusBarColor = statusBarColor
        background.navigationBarColor = navigationBarColor
        (window.decorView as? ViewGroup)?.let { decor ->
            if (background.statusBarHeight == 0) {
                background.statusBarHeight = getSystemBarHeight(decor, "status_bar_height")
            }
            if (background.navigationBarHeight == 0) {
                background.navigationBarHeight = getSystemBarHeight(decor, "navigation_bar_height")
            }
            ViewCompat.getRootWindowInsets(decor)?.let { insets ->
                background.statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                background.navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            }
        }
        window.setBackgroundDrawable(background)
        background.invalidateSelf()
        return background
    }

    private fun drawSystemBarBackgrounds(
        window: Window,
        statusBarColor: Int,
        navigationBarColor: Int,
        barsBackground: SystemBarsBackground,
    ) {
        ensureSystemBarBackground(
            window = window,
            tag = STATUS_BAR_BACKGROUND_TAG,
            color = statusBarColor,
            gravity = Gravity.TOP,
            fallbackResourceName = "status_bar_height",
            onHeight = { height ->
                barsBackground.statusBarHeight = height
                barsBackground.invalidateSelf()
            },
        ) { insets -> insets.getInsets(WindowInsetsCompat.Type.statusBars()).top }

        ensureSystemBarBackground(
            window = window,
            tag = NAVIGATION_BAR_BACKGROUND_TAG,
            color = navigationBarColor,
            gravity = Gravity.BOTTOM,
            fallbackResourceName = "navigation_bar_height",
            onHeight = { height ->
                barsBackground.navigationBarHeight = height
                barsBackground.invalidateSelf()
            },
        ) { insets -> insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom }
    }

    private fun ensureSystemBarBackground(
        window: Window,
        tag: String,
        color: Int,
        gravity: Int,
        fallbackResourceName: String,
        onHeight: (Int) -> Unit,
        getHeight: (WindowInsetsCompat) -> Int
    ) {
        val decor = window.decorView as? ViewGroup ?: return
        val background = decor.findViewWithTag<View>(tag) ?: View(decor.context).also { view ->
            view.tag = tag
            view.visibility = View.GONE
            decor.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    getSystemBarHeight(decor, fallbackResourceName),
                    gravity
                )
            )
        }

        if (decor.indexOfChild(background) != decor.childCount - 1) {
            val layoutParams = background.layoutParams
            decor.removeView(background)
            decor.addView(background, layoutParams)
        }

        background.setBackgroundColor(color)
        ViewCompat.setOnApplyWindowInsetsListener(background) { view, insets ->
            val height = getHeight(insets)
            val decorPadding = if (gravity == Gravity.TOP) decor.paddingTop else decor.paddingBottom
            setSystemBarBackgroundHeight(view, if (decorPadding > 0) 0 else height)
            onHeight(height)
            insets
        }
        ViewCompat.requestApplyInsets(background)
    }

    private fun setSystemBarBackgroundHeight(view: View, height: Int) {
        view.visibility = if (height > 0) View.VISIBLE else View.GONE
        val layoutParams = view.layoutParams
        if (layoutParams.height != height) {
            layoutParams.height = height
            view.layoutParams = layoutParams
        }
    }

    private fun getSystemBarHeight(decor: ViewGroup, resourceName: String): Int {
        val resourceId = decor.resources.getIdentifier(resourceName, "dimen", "android")
        if (resourceId == 0) {
            return 0
        }
        return decor.resources.getDimensionPixelSize(resourceId)
    }

    private fun fitContentBelowSystemBars(window: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return
        }

        setContentFitsSystemBars(window, true)
    }

    private fun setContentFitsSystemBars(window: Window, fitsSystemWindows: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return
        }

        val content = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) ?: return
        root.fitsSystemWindows = fitsSystemWindows
        if (!fitsSystemWindows) {
            root.setPadding(0, 0, 0, 0)
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun showSystemBars(window: Window) {
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    private fun setSystemBarBackgroundsVisible(window: Window, visible: Boolean) {
        val decor = window.decorView as? ViewGroup ?: return
        val visibility = if (visible) View.VISIBLE else View.GONE
        decor.findViewWithTag<View>(STATUS_BAR_BACKGROUND_TAG)?.visibility = visibility
        decor.findViewWithTag<View>(NAVIGATION_BAR_BACKGROUND_TAG)?.visibility = visibility
    }

    private class SystemBarsBackground : Drawable() {

        var baseColor = 0
        var statusBarColor = 0
        var navigationBarColor = 0
        var statusBarHeight = 0
        var navigationBarHeight = 0

        private val paint = Paint()

        override fun draw(canvas: Canvas) {
            paint.color = baseColor
            canvas.drawRect(bounds, paint)
            if (statusBarHeight > 0) {
                paint.color = statusBarColor
                canvas.drawRect(
                    bounds.left.toFloat(),
                    bounds.top.toFloat(),
                    bounds.right.toFloat(),
                    (bounds.top + statusBarHeight).toFloat(),
                    paint,
                )
            }
            if (navigationBarHeight > 0) {
                paint.color = navigationBarColor
                canvas.drawRect(
                    bounds.left.toFloat(),
                    (bounds.bottom - navigationBarHeight).toFloat(),
                    bounds.right.toFloat(),
                    bounds.bottom.toFloat(),
                    paint,
                )
            }
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }

}
