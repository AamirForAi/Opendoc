package com.gitlab.mudlej.MjPdfReader.ui.main

import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.enums.ConfigurableAction

class ConfigurableActionResolver(
    private val hasFile: () -> Boolean,
    private val handlers: Handlers,
) {

    data class Handlers(
        val toggleFullscreen: () -> Unit,
        val exitFullscreen: () -> Unit,
        val rotate: () -> Unit,
        val toggleHorizontalLock: () -> Unit,
        val toggleZoomLock: () -> Unit,
        val screenshot: () -> Unit,
        val reload: () -> Unit,
        val openLocal: () -> Unit,
        val openOnline: () -> Unit,
        val search: () -> Unit,
        val goToPage: () -> Unit,
        val textMode: () -> Unit,
        val share: () -> Unit,
        val settings: () -> Unit,
        val fileMetadata: () -> Unit,
        val about: () -> Unit,
        val configuration: () -> Unit,
        val tableOfContents: () -> Unit,
        val linksInFile: () -> Unit,
        val print: () -> Unit,
    )

    fun action(actionId: String): ConfiguredAction? {
        return action(ConfigurableAction.fromId(actionId))
    }

    fun action(action: ConfigurableAction): ConfiguredAction? {
        val fileAvailable = hasFile()
        return when (action) {
            ConfigurableAction.NONE -> null
            ConfigurableAction.FULLSCREEN -> ConfiguredAction(
                R.string.full_screen,
                R.drawable.ic_fullscreen_grey,
                visible = fileAvailable,
                run = handlers.toggleFullscreen,
            )
            ConfigurableAction.EXIT_FULLSCREEN -> ConfiguredAction(
                R.string.exit,
                R.drawable.close_icon,
                visible = fileAvailable,
                run = handlers.exitFullscreen,
            )
            ConfigurableAction.ROTATE -> ConfiguredAction(
                R.string.rotate,
                R.drawable.ic_screen_rotate,
                visible = fileAvailable,
                run = handlers.rotate,
            )
            ConfigurableAction.HORIZONTAL_LOCK -> ConfiguredAction(
                R.string.horizontal_lock_action,
                R.drawable.ic_horizontal_swipe,
                visible = fileAvailable,
                run = handlers.toggleHorizontalLock,
            )
            ConfigurableAction.ZOOM_LOCK -> ConfiguredAction(
                R.string.zoom_lock,
                R.drawable.ic_zoom_out,
                visible = fileAvailable,
                run = handlers.toggleZoomLock,
            )
            ConfigurableAction.SCREENSHOT -> ConfiguredAction(
                R.string.screenshot,
                R.drawable.ic_screenshot,
                visible = fileAvailable,
                run = handlers.screenshot,
            )
            ConfigurableAction.RELOAD -> ConfiguredAction(
                R.string.reload_pdf,
                R.drawable.ic_refresh,
                visible = fileAvailable,
                run = handlers.reload,
            )
            ConfigurableAction.OPEN_LOCAL -> ConfiguredAction(
                R.string.open_another_pdf,
                R.drawable.ic_folder,
                run = handlers.openLocal,
            )
            ConfigurableAction.OPEN_ONLINE -> ConfiguredAction(
                R.string.open_online_pdf,
                R.drawable.ic_link,
                run = handlers.openOnline,
            )
            ConfigurableAction.SEARCH -> ConfiguredAction(
                R.string.search,
                R.drawable.search_icon,
                visible = fileAvailable,
                run = handlers.search,
            )
            ConfigurableAction.GO_TO_PAGE -> ConfiguredAction(
                R.string.go_to_page,
                R.drawable.ic_shortcut,
                visible = fileAvailable,
                run = handlers.goToPage,
            )
            ConfigurableAction.TEXT_MODE -> ConfiguredAction(
                R.string.text_mode,
                R.drawable.ic_text,
                visible = fileAvailable,
                run = handlers.textMode,
            )
            ConfigurableAction.SHARE -> ConfiguredAction(
                R.string.share_file,
                R.drawable.ic_share,
                visible = fileAvailable,
                run = handlers.share,
            )
            ConfigurableAction.SETTINGS -> ConfiguredAction(
                R.string.settings,
                R.drawable.ic_settings,
                run = handlers.settings,
            )
            ConfigurableAction.FILE_METADATA -> ConfiguredAction(
                R.string.file_metadata,
                R.drawable.meta_info,
                visible = fileAvailable,
                run = handlers.fileMetadata,
            )
            ConfigurableAction.ABOUT -> ConfiguredAction(
                R.string.action_about,
                R.drawable.info_icon,
                run = handlers.about,
            )
            ConfigurableAction.CONFIGURATION -> ConfiguredAction(
                R.string.advanced_config,
                R.drawable.ic_display_settings,
                run = handlers.configuration,
            )
            ConfigurableAction.TABLE_OF_CONTENTS -> ConfiguredAction(
                R.string.table_of_contents,
                R.drawable.ic_book_bookmark,
                visible = fileAvailable,
                run = handlers.tableOfContents,
            )
            ConfigurableAction.LINKS_IN_FILE -> ConfiguredAction(
                R.string.links_in_file,
                R.drawable.ic_link,
                visible = fileAvailable,
                run = handlers.linksInFile,
            )
            ConfigurableAction.PRINT -> ConfiguredAction(
                R.string.print_file,
                R.drawable.ic_print,
                visible = fileAvailable,
                run = handlers.print,
            )
            ConfigurableAction.BRIGHTNESS,
            ConfigurableAction.AUTO_SCROLL,
            ConfigurableAction.TOGGLE_LABELS -> null
        }
    }

    fun perform(actionId: String): Boolean {
        val action = action(actionId) ?: return false
        if (!action.visible) {
            return false
        }
        action.run()
        return true
    }
}
