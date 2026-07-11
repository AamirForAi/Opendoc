/*
 *   MJ PDF
 *   Copyright (C) 2023 Mudlej
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *  --------------------------
 *  This code was previously licensed under
 *
 *  MIT License
 *
 *  Copyright (c) 2018 Gokul Swaminathan
 *  Copyright (c) 2023 Mudlej
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package com.gitlab.mudlej.MjPdfReader.core.text

fun ignoreCaseOpt(ignoreCase: Boolean) =
    if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()

fun String?.indexesOf(pat: String, ignoreCase: Boolean = true): List<Int> =
    Regex.escape(pat)       // to disable any special meaning of query's characters
        .toRegex(ignoreCaseOpt(ignoreCase))
        .findAll(this?: "")
        .map { it.range.first }
        .toList()

class FoldedText private constructor(
    private val original: String,
    private val folded: String,
    private val originalIndices: IntArray,
) {
    fun findMatchRanges(foldedPattern: String): List<IntRange> {
        if (folded.isEmpty() || foldedPattern.isEmpty()) return emptyList()

        val ranges = mutableListOf<IntRange>()
        var foldedIndex = folded.indexOf(foldedPattern)
        while (foldedIndex != -1) {
            val foldedEnd = foldedIndex + foldedPattern.length
            val start = originalIndices[foldedIndex]
            var end = if (foldedEnd < originalIndices.size) originalIndices[foldedEnd] else original.length
            if (end <= start) {
                end = start + Character.charCount(original.codePointAt(start))
            }
            ranges.add(start until end)
            foldedIndex = folded.indexOf(foldedPattern, foldedEnd)
        }
        return ranges
    }

    companion object {
        private const val ARABIC_TATWEEL = 'ـ'

        fun of(text: String): FoldedText {
            val folded = StringBuilder(text.length)
            val originalIndices = ArrayList<Int>(text.length)
            var i = 0
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                val charCount = Character.charCount(codePoint)
                if (codePoint < 0x80) {
                    folded.append(codePoint.toChar().lowercaseChar())
                    originalIndices.add(i)
                } else {
                    val decomposed = java.text.Normalizer.normalize(
                        text.substring(i, i + charCount),
                        java.text.Normalizer.Form.NFKD,
                    )
                    for (ch in decomposed) {
                        if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
                        if (ch == ARABIC_TATWEEL) continue
                        folded.append(ch.lowercaseChar())
                        originalIndices.add(i)
                    }
                }
                i += charCount
            }
            return FoldedText(text, folded.toString(), originalIndices.toIntArray())
        }

        fun foldPattern(pattern: String): String = of(pattern).folded
    }
}

fun String.accentInsensitiveRanges(pattern: String): List<IntRange> {
    if (isEmpty() || pattern.isEmpty()) return emptyList()
    return FoldedText.of(this).findMatchRanges(FoldedText.foldPattern(pattern))
}

fun String.containsAccentInsensitive(pattern: String): Boolean =
    accentInsensitiveRanges(pattern).isNotEmpty()
