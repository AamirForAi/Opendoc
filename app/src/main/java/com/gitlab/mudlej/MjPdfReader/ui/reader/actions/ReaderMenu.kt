package com.gitlab.mudlej.MjPdfReader.ui.reader.actions

import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

class ReaderMenu(
    private val activity: MainActivity,
    private val actionResolver: ConfigurableActionResolver,
    private val hasFile: () -> Boolean,
    private val toggleSecondBar: () -> Unit,
) {

    fun show() {
        showReaderActionsDialog(activity, menuContent())
    }

    private fun menuContent(): ReaderMenuContent {
        return ReaderMenuContent(
            sections = listOf(
                ReaderMenuSection(R.string.reader_menu_actions, actionsSection()),
                ReaderMenuSection(R.string.reader_menu_pages, pagesSection()),
            ),
        )
    }

    private fun actionsSection(): List<ReaderAction> {
        return listOfNotNull(
            action(ConfigurableAction.OPEN_LOCAL),
            action(ConfigurableAction.SWITCH_THEME),
            action(ConfigurableAction.SEARCH),
            action(ConfigurableAction.GO_TO_PAGE),
            action(ConfigurableAction.BOOKMARK_PAGE),
            action(ConfigurableAction.FULLSCREEN),
            action(ConfigurableAction.READING_DIRECTION),
            action(ConfigurableAction.CROP_MARGINS),
            action(ConfigurableAction.SCREENSHOT),
            action(ConfigurableAction.EXTRACT_TEXT),
            action(ConfigurableAction.SHARE),
            action(ConfigurableAction.PRINT),
            action(ConfigurableAction.ADD_SIGNATURE),
            action(ConfigurableAction.RELOAD),
            action(ConfigurableAction.OPEN_ONLINE),
            action(ConfigurableAction.FILE_METADATA),
            ReaderAction(R.string.toggle_shortcuts, R.drawable.ic_awesome, visible = hasFile()) {
                toggleSecondBar()
            },
            navAction(ConfigurableAction.NAV_BACK),
            navAction(ConfigurableAction.NAV_FORWARD),
            navAction(ConfigurableAction.NAV_HISTORY),
        )
    }

    private fun pagesSection(): List<ReaderAction> {
        return listOfNotNull(
            action(ConfigurableAction.TABLE_OF_CONTENTS),
            action(ConfigurableAction.USER_BOOKMARKS),
            action(ConfigurableAction.TEXT_MODE),
            action(ConfigurableAction.LINKS_IN_FILE),
            action(ConfigurableAction.SETTINGS),
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

    private fun navAction(action: ConfigurableAction): ReaderAction? {
        val configuredAction = actionResolver.action(action) ?: return null
        return ReaderAction(
            configuredAction.titleRes,
            configuredAction.iconRes,
            visible = hasFile(),
            enabled = configuredAction.visible,
        ) {
            configuredAction.run()
        }
    }
}
