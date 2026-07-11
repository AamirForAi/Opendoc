package com.gitlab.mudlej.MjPdfReader.ui.tableofcontents

import com.gitlab.mudlej.MjPdfReader.pdf.TableOfContentsEntry

interface TableOfContentsFunctions {
    fun onEntryClicked(entry: TableOfContentsEntry)
}