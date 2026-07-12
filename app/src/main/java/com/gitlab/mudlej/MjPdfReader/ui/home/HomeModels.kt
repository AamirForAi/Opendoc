// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

enum class HomeTab { RECENT, LIBRARY, FOLDERS }

enum class HomeViewMode { GRID, LIST }

enum class HomeGridSize(val targetCellDp: Int) { SMALL(96), MEDIUM(120), LARGE(150) }

enum class HomeSortOrder { LAST_OPENED, NAME }

enum class ListFilter { RECENT, ALL, FAVORITE, TO_READ, READING, ON_HOLD, COMPLETED, ABANDONED }

enum class ScanMode { NOT_CONFIGURED, WHOLE_DEVICE, SELECTED_LOCATIONS }
