package com.gitlab.mudlej.MjPdfReader.ui.reader

interface ReaderUi {
    fun updateTitle()
    fun updateActionBar()
    fun updateDirtyUi()
    fun updateDirtyUiPosition()
    fun hideProgress()
    fun checkHasFile(): Boolean
    fun runAfterDirtyAnnotationPrompt(discardAction: () -> Unit)
}
