// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import java.io.File

object ScanScope {

    fun normalize(paths: Set<String>): List<String> {
        val cleaned = paths
            .map { it.trimEnd(File.separatorChar) }
            .filter { it.isNotBlank() }
            .sortedBy { it.length }
        val kept = mutableListOf<String>()
        for (path in cleaned) {
            if (kept.none { path == it || path.startsWith("$it${File.separatorChar}") }) {
                kept.add(path)
            }
        }
        return kept
    }

    fun displayRoots(mode: ScanMode, locations: Set<String>): List<String>? {
        return when (mode) {
            ScanMode.WHOLE_DEVICE -> null
            ScanMode.SELECTED_LOCATIONS -> normalize(locations)
            ScanMode.NOT_CONFIGURED -> emptyList()
        }
    }

    fun contains(roots: List<String>?, path: String): Boolean {
        if (roots == null) {
            return true
        }
        return roots.any { path == it || path.startsWith("$it${File.separatorChar}") }
    }
}
