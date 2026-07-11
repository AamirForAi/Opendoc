// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.text

import java.text.Normalizer

object NormalizedTextMapper {

    fun toRawRange(rawText: String, normalizedStart: Int, normalizedLength: Int): IntRange? {
        if (rawText.isEmpty() || normalizedStart < 0 || normalizedLength <= 0) {
            return null
        }
        val normalizedEnd = normalizedStart + normalizedLength
        var rawIndex = 0
        var normalizedCount = 0
        var rawStart = -1
        while (rawIndex < rawText.length) {
            if (rawStart < 0 && normalizedCount >= normalizedStart) {
                rawStart = rawIndex
            }
            if (rawStart >= 0 && normalizedCount >= normalizedEnd) {
                return if (rawIndex > rawStart) rawStart until rawIndex else null
            }
            val chunkEnd = chunkEnd(rawText, rawIndex)
            normalizedCount += normalizedLength(rawText.substring(rawIndex, chunkEnd))
            rawIndex = chunkEnd
        }
        return if (rawStart in 0 until rawText.length) rawStart until rawText.length else null
    }

    private fun chunkEnd(text: String, start: Int): Int {
        if (text.startsWith("\r\n", start)) {
            return start + 2
        }
        var end = start + Character.charCount(text.codePointAt(start))
        while (end < text.length) {
            val codePoint = text.codePointAt(end)
            if (!isCombining(codePoint)) {
                break
            }
            end += Character.charCount(codePoint)
        }
        return end
    }

    private fun isCombining(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
    }

    private fun normalizedLength(chunk: String): Int {
        return Normalizer.normalize(chunk, Normalizer.Form.NFKC)
            .replace('\uFFFE', '-')
            .replace("\u200B", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .length
    }
}
