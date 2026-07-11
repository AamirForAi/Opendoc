package com.gitlab.mudlej.MjPdfReader.ui.links

import com.gitlab.mudlej.MjPdfReader.pdf.Link

interface LinkFunctions {

    fun onLinkClicked(link: Link)

    fun onPageNumberClicked(link: Link)

    fun onCopyLinkClicked(link: Link)

}