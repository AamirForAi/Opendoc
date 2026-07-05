package com.gitlab.mudlej.MjPdfReader.ui.text_reader

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import com.gitlab.mudlej.MjPdfReader.R
import com.google.android.material.color.MaterialColors

data class TextReaderSettings(
    val fontSize: Float = DEFAULT_FONT_SIZE,
    val lineSpacing: Float = DEFAULT_LINE_SPACING,
    val horizontalMargin: Int = DEFAULT_HORIZONTAL_MARGIN,
    val theme: ReaderTheme = ReaderTheme.SYSTEM,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SANS,
) {
    fun save(preferences: SharedPreferences) {
        preferences.edit()
            .putFloat(FONT_SIZE_KEY, fontSize)
            .putFloat(LINE_SPACING_KEY, lineSpacing)
            .putInt(HORIZONTAL_MARGIN_KEY, horizontalMargin)
            .putString(THEME_KEY, theme.name)
            .putString(FONT_FAMILY_KEY, fontFamily.name)
            .apply()
    }

    companion object {
        const val DEFAULT_FONT_SIZE = 18f
        const val DEFAULT_LINE_SPACING = 1.35f
        const val DEFAULT_HORIZONTAL_MARGIN = 20

        private const val FONT_SIZE_KEY = "textReaderFontSize"
        private const val LINE_SPACING_KEY = "textReaderLineSpacing"
        private const val HORIZONTAL_MARGIN_KEY = "textReaderHorizontalMargin"
        private const val THEME_KEY = "textReaderTheme"
        private const val FONT_FAMILY_KEY = "textReaderFontFamily"

        fun load(preferences: SharedPreferences): TextReaderSettings {
            return TextReaderSettings(
                fontSize = preferences.getFloat(FONT_SIZE_KEY, DEFAULT_FONT_SIZE),
                lineSpacing = preferences.getFloat(LINE_SPACING_KEY, DEFAULT_LINE_SPACING),
                horizontalMargin = preferences.getInt(HORIZONTAL_MARGIN_KEY, DEFAULT_HORIZONTAL_MARGIN),
                theme = preferences.getString(THEME_KEY, ReaderTheme.SYSTEM.name)
                    ?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() }
                    ?: ReaderTheme.SYSTEM,
                fontFamily = preferences.getString(FONT_FAMILY_KEY, ReaderFontFamily.SANS.name)
                    ?.let { runCatching { ReaderFontFamily.valueOf(it) }.getOrNull() }
                    ?: ReaderFontFamily.SANS,
            )
        }
    }
}

enum class ReaderTheme {
    SYSTEM,
    LIGHT,
    SEPIA,
    DARK,
    BLACK,
    DRACULA;

    fun colors(view: View): ReaderThemeColors {
        return when (this) {
            SYSTEM -> ReaderThemeColors(
                background = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface, Color.WHITE),
                text = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface, Color.BLACK),
                label = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY),
            )
            LIGHT -> ReaderThemeColors(Color.rgb(250, 250, 247), Color.rgb(28, 28, 28), Color.rgb(94, 94, 94))
            SEPIA -> ReaderThemeColors(Color.rgb(244, 236, 216), Color.rgb(43, 33, 24), Color.rgb(105, 82, 58))
            DARK -> ReaderThemeColors(Color.rgb(30, 31, 34), Color.rgb(232, 234, 237), Color.rgb(176, 180, 186))
            BLACK -> ReaderThemeColors(Color.BLACK, Color.rgb(238, 238, 238), Color.rgb(180, 180, 180))
            DRACULA -> ReaderThemeColors(Color.rgb(40, 42, 54), Color.rgb(248, 248, 242), Color.rgb(98, 114, 164))
        }
    }
}

data class ReaderThemeColors(
    val background: Int,
    val text: Int,
    val label: Int,
)

enum class ReaderFontFamily {
    SANS,
    SERIF,
    MONO;

    fun typeface(): Typeface {
        return when (this) {
            SANS -> Typeface.SANS_SERIF
            SERIF -> Typeface.SERIF
            MONO -> Typeface.MONOSPACE
        }
    }

    fun label(context: Context): String {
        return when (this) {
            SANS -> context.getString(R.string.text_reader_font_sans)
            SERIF -> context.getString(R.string.text_reader_font_serif)
            MONO -> context.getString(R.string.text_reader_font_mono)
        }
    }
}
