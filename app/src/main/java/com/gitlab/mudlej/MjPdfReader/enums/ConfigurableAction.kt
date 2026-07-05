package com.gitlab.mudlej.MjPdfReader.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R

enum class ConfigurableAction(
    val id: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
) {
    NONE("none", R.string.none, 0),
    FULLSCREEN("fullscreen", R.string.full_screen, R.drawable.ic_fullscreen_grey),
    EXIT_FULLSCREEN("exit_fullscreen", R.string.exit, R.drawable.close_icon),
    ROTATE("rotate", R.string.rotate, R.drawable.ic_screen_rotate),
    BRIGHTNESS("brightness", R.string.brightness, R.drawable.ic_brightness),
    AUTO_SCROLL("auto_scroll", R.string.auto_scroll, R.drawable.ic_auto_scroll),
    HORIZONTAL_LOCK("horizontal_lock", R.string.horizontal_lock_action, R.drawable.ic_horizontal_swipe),
    READING_DIRECTION("reading_direction", R.string.reading_direction, R.drawable.ic_horizontal_swipe),
    ZOOM_LOCK("zoom_lock", R.string.zoom_lock, R.drawable.ic_zoom_out),
    CROP_MARGINS("crop_margins", R.string.crop_margins, R.drawable.ic_crop_margins),
    SCREENSHOT("screenshot", R.string.screenshot, R.drawable.ic_screenshot),
    TOGGLE_LABELS("toggle_labels", R.string.hide_labels, R.drawable.ic_double_arrow_left),
    SWITCH_THEME("switch_theme", R.string.switch_theme, R.drawable.ic_toggle_theme),
    RELOAD("reload", R.string.reload_pdf, R.drawable.ic_refresh),
    OPEN_LOCAL("open_local", R.string.open_another_pdf, R.drawable.ic_folder),
    OPEN_ONLINE("open_online", R.string.open_online_pdf, R.drawable.ic_link),
    SEARCH("search", R.string.search, R.drawable.search_icon),
    GO_TO_PAGE("go_to_page", R.string.go_to_page, R.drawable.ic_shortcut),
    EXTRACT_TEXT("extract_text", R.string.copy_page_text, R.drawable.ic_copy),
    TEXT_MODE("text_mode", R.string.text_mode, R.drawable.ic_text),
    SHARE("share", R.string.share_file, R.drawable.ic_share),
    SETTINGS("settings", R.string.settings, R.drawable.ic_settings),
    FILE_METADATA("file_metadata", R.string.file_metadata, R.drawable.meta_info),
    ABOUT("about", R.string.action_about, R.drawable.info_icon),
    TABLE_OF_CONTENTS("table_of_contents", R.string.table_of_contents, R.drawable.ic_book_bookmark),
    LINKS_IN_FILE("links_in_file", R.string.links_in_file, R.drawable.ic_links_in_file),
    PRINT("print", R.string.print_file, R.drawable.ic_print);

    companion object {
        val toolbarActions = listOf(
            NONE,
            FULLSCREEN,
            ROTATE,
            HORIZONTAL_LOCK,
            READING_DIRECTION,
            ZOOM_LOCK,
            CROP_MARGINS,
            SCREENSHOT,
            SWITCH_THEME,
            RELOAD,
            OPEN_LOCAL,
            OPEN_ONLINE,
            SEARCH,
            GO_TO_PAGE,
            TEXT_MODE,
            SHARE,
            SETTINGS,
            FILE_METADATA,
            ABOUT,
            TABLE_OF_CONTENTS,
            LINKS_IN_FILE,
            PRINT,
        )

        val fullScreenOverlayActions = listOf(
            ROTATE,
            BRIGHTNESS,
            AUTO_SCROLL,
            HORIZONTAL_LOCK,
            READING_DIRECTION,
            ZOOM_LOCK,
            CROP_MARGINS,
            SCREENSHOT,
            TOGGLE_LABELS,
            SWITCH_THEME,
            RELOAD,
            SEARCH,
            GO_TO_PAGE,
            TEXT_MODE,
            TABLE_OF_CONTENTS,
        )

        val shortcutBarActions = listOf(
            SWITCH_THEME,
            FULLSCREEN,
            ROTATE,
            HORIZONTAL_LOCK,
            READING_DIRECTION,
            ZOOM_LOCK,
            CROP_MARGINS,
            SCREENSHOT,
            RELOAD,
            OPEN_LOCAL,
            OPEN_ONLINE,
            SEARCH,
            GO_TO_PAGE,
            EXTRACT_TEXT,
            TEXT_MODE,
            SHARE,
            SETTINGS,
            FILE_METADATA,
            ABOUT,
            TABLE_OF_CONTENTS,
            LINKS_IN_FILE,
            PRINT,
        )

        val defaultShortcutBarSelectedActions = listOf(
            SWITCH_THEME,
            OPEN_LOCAL,
            EXTRACT_TEXT,
            TABLE_OF_CONTENTS,
            SHARE,
            SEARCH,
            GO_TO_PAGE,
        )

        val defaultShortcutBarOrder = defaultShortcutBarSelectedActions + shortcutBarActions.filterNot {
            defaultShortcutBarSelectedActions.contains(it)
        }

        val defaultShortcutBarActionIds = defaultShortcutBarSelectedActions.map { it.id }.toSet()

        val requiredFullScreenOverlayActionIds = setOf(EXIT_FULLSCREEN.id)

        val defaultFullScreenOverlayOrder = listOf(EXIT_FULLSCREEN) + fullScreenOverlayActions

        val defaultFullScreenOverlayActionIds = setOf(
            ROTATE.id,
            BRIGHTNESS.id,
            AUTO_SCROLL.id,
            HORIZONTAL_LOCK.id,
            ZOOM_LOCK.id,
            CROP_MARGINS.id,
            SCREENSHOT.id,
            TOGGLE_LABELS.id,
        ) + requiredFullScreenOverlayActionIds

        val dynamicFullScreenOverlayActions = listOf(
            SWITCH_THEME,
            RELOAD,
            CROP_MARGINS,
            READING_DIRECTION,
            SEARCH,
            GO_TO_PAGE,
            TEXT_MODE,
            TABLE_OF_CONTENTS,
        )

        fun fromId(id: String?): ConfigurableAction {
            return values().firstOrNull { it.id == id } ?: NONE
        }
    }
}
