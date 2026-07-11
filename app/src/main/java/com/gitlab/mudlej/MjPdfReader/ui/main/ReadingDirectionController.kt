package com.gitlab.mudlej.MjPdfReader.ui.main

import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.DocumentState
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.enums.ReadingDirection
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManager
import com.gitlab.mudlej.MjPdfReader.util.computeHash
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ReadingDirectionController(
    private val activity: MainActivity,
    private val pdf: DocumentState,
    private val vm: ReaderViewModel,
    private val pref: Preferences,
    private val databaseManager: DatabaseManager,
    private val scope: CoroutineScope,
    private val resolver: ReadingDirectionResolver,
    private val documentLoadController: DocumentLoadController,
) {

    fun showDialog() {
        var selectedOverride = pdf.readingDirectionOverride
        val dialogBuilder = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.reading_direction)
            .setSingleChoiceItems(
                dialogItems(),
                selectedIndexFor(selectedOverride),
            ) { _, which ->
                selectedOverride = overrideForIndex(which)
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                applyOverride(selectedOverride)
            }
            .setNegativeButton(android.R.string.cancel, null)
        if (!pref.getHorizontalScroll()) {
            dialogBuilder.setMessage(R.string.reading_direction_message)
        }
        dialogBuilder.show()
    }

    private fun dialogItems(): Array<String> {
        val autoLabel = if (pdf.effectiveReadingDirection.isRightToLeft) {
            R.string.reading_direction_auto_rtl
        } else {
            R.string.reading_direction_auto_ltr
        }
        return arrayOf(
            activity.getString(autoLabel),
            activity.getString(R.string.reading_direction_ltr),
            activity.getString(R.string.reading_direction_rtl),
        )
    }

    private fun selectedIndexFor(direction: ReadingDirection?): Int {
        return when (direction) {
            null -> 0
            ReadingDirection.LEFT_TO_RIGHT -> 1
            ReadingDirection.RIGHT_TO_LEFT -> 2
            ReadingDirection.UNKNOWN -> 0
        }
    }

    private fun overrideForIndex(index: Int): ReadingDirection? {
        return when (index) {
            1 -> ReadingDirection.LEFT_TO_RIGHT
            2 -> ReadingDirection.RIGHT_TO_LEFT
            else -> null
        }
    }

    private fun applyOverride(direction: ReadingDirection?) {
        val loadToken = vm.currentLoadToken
        val documentUri = pdf.uri
        val oldEffectiveDirection = pdf.effectiveReadingDirection
        scope.launch {
            val hash = pdf.fileHash ?: computeHash(activity, pdf)
            if (!vm.isCurrent(loadToken, documentUri)) {
                return@launch
            }
            if (hash == null) {
                documentLoadController.showFailedToComputeHashError()
                return@launch
            }

            pdf.fileHash = hash
            databaseManager.setReadingDirectionOverride(hash, direction?.id)
            if (!vm.isCurrent(loadToken, documentUri)) {
                return@launch
            }

            val detectedDirection = if (direction == null && pdf.detectedReadingDirection == null) {
                resolver.detectIfNeeded(documentUri)
            } else {
                pdf.detectedReadingDirection
            }
            if (!vm.isCurrent(loadToken, documentUri)) {
                return@launch
            }
            detectedDirection?.let { databaseManager.setDetectedReadingDirection(hash, it.id) }

            pdf.readingDirectionOverride = direction
            pdf.detectedReadingDirection = detectedDirection
            pdf.effectiveReadingDirection = ReadingDirection.effective(direction, detectedDirection)
            if (pref.getHorizontalScroll() && pdf.effectiveReadingDirection != oldEffectiveDirection) {
                activity.recreate()
            }
        }
    }
}
