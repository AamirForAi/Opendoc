package com.gitlab.mudlej.MjPdfReader.ui.main

import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.enums.ConfigurableAction

class ReaderMenu(
    private val activity: MainActivity,
    private val actionResolver: ConfigurableActionResolver,
    private val hasFile: () -> Boolean,
    private val toggleSecondBar: () -> Unit,
) {

    fun show() {
        showReaderActionsDialog(activity, actions())
    }

    private fun actions(): List<ReaderAction> {
        return listOfNotNull(
            action(ConfigurableAction.SWITCH_THEME),
            action(ConfigurableAction.OPEN_LOCAL),
            action(ConfigurableAction.OPEN_ONLINE),
            action(ConfigurableAction.TABLE_OF_CONTENTS),
            action(ConfigurableAction.FULLSCREEN),
            action(ConfigurableAction.SEARCH),
            action(ConfigurableAction.GO_TO_PAGE),
            action(ConfigurableAction.READING_DIRECTION),
            action(ConfigurableAction.CROP_MARGINS),
            action(ConfigurableAction.EXTRACT_TEXT),
            action(ConfigurableAction.ADD_SIGNATURE),
            action(ConfigurableAction.SETTINGS),
            ReaderAction(R.string.toggle_shortcuts, R.drawable.ic_awesome, visible = hasFile()) {
                toggleSecondBar()
            },
            action(ConfigurableAction.TEXT_MODE),
            action(ConfigurableAction.LINKS_IN_FILE),
            action(ConfigurableAction.SHARE),
            action(ConfigurableAction.PRINT),
            action(ConfigurableAction.FILE_METADATA),
            action(ConfigurableAction.ABOUT),
        )
    }

    private fun action(action: ConfigurableAction): ReaderAction? {
        val configuredAction = actionResolver.action(action) ?: return null
        return ReaderAction(
            configuredAction.titleRes,
            configuredAction.iconRes,
            visible = configuredAction.visible,
        ) {
            configuredAction.run()
        }
    }
}
