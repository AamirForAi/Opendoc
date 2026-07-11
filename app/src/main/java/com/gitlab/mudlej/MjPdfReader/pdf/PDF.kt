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

package com.gitlab.mudlej.MjPdfReader.pdf

object PDF {

    // constants
    const val FILE_TYPE = "application/pdf"
    const val HASH_SIZE = 1024 * 1024
    const val TABLE_OF_CONTENTS_TEXT_SIZE = 24F
    const val TABLE_OF_CONTENTS_TEXT_SIZE_DEC = 2F
    const val TABLE_OF_CONTENTS_RESULT_OK = 48645
    const val SEARCH_RESULT_OK = 48632
    const val LINK_RESULT_OK = 48032
    const val SCREENSHOT_IMAGE_QUALITY = 100
    const val SEARCH_RESULT_OFFSET = 40
    const val ADDITIONAL_SEARCH_RESULT_OFFSET = 100
    const val TOO_MANY_RESULTS = 3500
    const val RESET_NUMBER = -1
    const val MIN_SEARCH_QUERY = 3

    // keys
    const val nameKey = "name"
    const val passwordKey = "password"
    const val pageNumberKey = "pageNumber"
    const val lengthKey = "length"
    const val uriKey = "uri"
    const val viewStateSavedKey = "viewStateSaved"
    const val viewStateZoomKey = "viewStateZoom"
    const val viewStatePageIndexKey = "viewStatePageIndex"
    const val viewStateSwipeVerticalKey = "viewStateSwipeVertical"
    const val viewStateHorizontalReadingDirectionRtlKey = "viewStateHorizontalReadingDirectionRtl"
    const val viewStateRelativeCrossAxisCenterKey = "viewStateRelativeCrossAxisCenter"
    const val viewStatePageCenterOffsetRatioKey = "viewStatePageCenterOffsetRatio"
    const val isPortraitKey = "isPortrait"
    const val isFullScreenToggledKey = "isFullScreenToggled"
    const val autoScrollSpeedKey = "autoScrollSpeedKey"
    const val cropMarginsEnabledKey = "cropMarginsEnabled"
    const val readingDirectionOverrideKey = "readingDirectionOverride"
    const val detectedReadingDirectionKey = "detectedReadingDirection"
    const val effectiveReadingDirectionKey = "effectiveReadingDirection"
    const val hasUnsavedAnnotationsKey = "hasUnsavedAnnotations"
    const val sessionOwnedAnnotationKeysKey = "sessionOwnedAnnotationKeys"
    const val chosenTableOfContentsEntryKey = "chosenTableOfContentsEntryKey"
    const val tableOfContentsExpandedPathsKey = "tableOfContentsExpandedPathsKey"
    const val tableOfContentsScrollPositionKey = "tableOfContentsScrollPositionKey"
    const val tableOfContentsScrollOffsetKey = "tableOfContentsScrollOffsetKey"
    const val tableOfContentsQueryKey = "tableOfContentsQueryKey"
    const val searchResultPageNumberKey = "searchResultPageNumberKey"
    const val fileHashKey = "fileHashKey"
    const val searchResultKey = "searchInput"
    const val linkResultKey = "linkResult"
    const val searchQueryKey = "searchQuery"
    const val searchQueryResultKey = "searchQueryResult"
    const val searchIgnoreAccentsKey = "searchIgnoreAccents"
    const val resultPositionInListKey = "searchResultPositionKey"
    const val filePathKey = "filePathKey"
}
