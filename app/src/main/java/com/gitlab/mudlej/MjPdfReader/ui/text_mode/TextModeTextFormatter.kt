// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.text_mode

object TextModeTextFormatter {
    fun format(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("[ \t]+\n"), "\n")
            .replace(Regex("(?<=\\p{L})-\n(?=\\p{L})"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
