// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import android.content.SharedPreferences
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeGridSize
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeSortOrder
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeViewMode
import com.gitlab.mudlej.MjPdfReader.ui.home.ListFilter

class Preferences(private val prefMan: SharedPreferences) {

    init {
        migrateLegacyKey("spaceBetweenPagesKey", spaceBetweenPagesKey)
        migrateLegacyKey("alwaysHorizontalKey", alwaysHorizontalKey)
    }

    private fun migrateLegacyKey(legacyKey: String, key: String) {
        if (!prefMan.contains(legacyKey)) {
            return
        }
        val editor = prefMan.edit()
        if (!prefMan.contains(key)) {
            editor.putBoolean(key, prefMan.getBoolean(legacyKey, false))
        }
        editor.remove(legacyKey).apply()
    }

    companion object {
        // Preferences keys
        const val firstInstallKey = "firstInstall"
        const val showFeaturesDialogKey = "showFeaturesDialog"
        const val highQualityKey = "highQuality"
        const val antiAliasingKey = "antiAliasing"
        const val horizontalScrollKey = "horizontalScroll"
        const val pageSnapKey = "pageSnap"
        const val pageFlingKey = "pageFling"
        const val browserScrollModeKey = "browserScrollMode"
        const val turnPageByMouseButtonsKey = "turnPageByMouseButtons"
        const val pdfDarkThemeKey = "pdfDarkTheme"
        const val appFollowSystemThemeKey = "appFollowSystemTheme"
        const val pdfFollowSystemThemeKey = "pdfFollowSystemTheme"
        const val interfaceThemeKey = "interfaceTheme"
        const val pdfPagesThemeKey = "pdfPagesTheme"
        const val enableReloadButtonKey = "enableReloadButton"
        const val primaryButtonActionKey = "primaryButtonAction"
        const val secondaryButtonActionKey = "secondaryButtonAction"
        const val fullScreenOverlayActionsKey = "fullScreenOverlayActions"
        const val fullScreenOverlayActionOrderKey = "fullScreenOverlayActionOrder"
        const val shortcutBarActionsKey = "shortcutBarActions"
        const val shortcutBarActionOrderKey = "shortcutBarActionOrder"
        const val screenOnKey = "screenOn"
        const val spaceBetweenPagesKey = "spaceBetweenPages"
        const val hideDelayKey = "hideDelay"
        const val partSizeKey = "partSize"
        const val thumbnailRatioKey = "thumbnailRatio"
        const val maxZoomKey = "maxZoom"
        const val inlineTextSelectionKey = "inlineTextSelection"
        const val detectExistingHighlightsKey = "detectExistingHighlights"
        const val highlightColorsKey = "highlightColors"
        const val searchIgnoreAccentsKey = "searchIgnoreAccents"
        const val defaultTextModeKey = "defaultTextMode"
        const val turnPageByVolumeButtonsKey = "turnPageByVolumeButtons"
        const val showScrollHandlePageCountKey = "showScrollHandlePageCount"
        const val showAppBarPageCountKey = "showAppBarPageCount"
        const val alwaysHideMarginsKey = "alwaysHideMargins"
        const val secondBarEnabledKey = "secondBarEnabled"
        const val hideButtonsLabelsKey = "hideButtonsLabels"
        const val fullScreenInfoShowTimeKey = "fullScreenInfoShowTime"
        const val fullScreenInfoShowPdfNameKey = "fullScreenInfoShowPdfName"
        const val fullScreenInfoShowPageNumberKey = "fullScreenInfoShowPageNumber"
        const val fullScreenInfoShowReadingPercentageKey = "fullScreenInfoShowReadingPercentage"
        const val doubleTapToExitEnabledKey = "doubleTapToExitEnabled"
        const val alwaysOpenAtFirstPageKey = "alwaysOpenAtFirstPage"
        const val autoFullScreenKey = "autoFullScreenSwitch"
        const val alwaysHorizontalKey = "alwaysHorizontal"
        const val scrollSpeedKey = "scrollSpeed"
        const val listFilterKey = "listFilter"
        const val homeDisabledKey = "homeDisabled"
        const val homeShowPdfTitleKey = "homeShowPdfTitle"
        const val homeTabKey = "homeTab"
        const val homeFolderFlatKey = "homeFolderFlat"
        const val homeViewModeKey = "homeViewMode"
        const val homeGridSizeKey = "homeGridSize"
        const val goToPageGridColumnsKey = "goToPageGridColumns"
        const val homeSortKey = "homeSort"

        // Default values
        const val firstInstallDefault = true
        const val showFeaturesDialogDefault = true
        const val highQualityDefault = false
        const val antiAliasingDefault = true
        const val horizontalScrollDefault = false
        const val pageSnapDefault = false
        const val pageFlingDefault = false
        const val browserScrollModeDefault = false
        const val turnPageByMouseButtonsDefault = true
        const val pdfDarkThemeDefault = false
        const val appFollowSystemThemeDefault = true    // NEW: for version v2.1 M3 Theme
        const val pdfFollowSystemThemeDefault = false
        const val enableReloadButtonDefault = false
        const val primaryButtonActionDefault = "fullscreen"
        const val secondaryButtonActionDefault = "none"
        const val annotationRenderingDefault = true
        const val screenOnDefault = false
        const val spaceBetweenPagesDefault = true
        const val hideDelayDefault = 3000
        const val spacingDefault = 10           // in dp
        const val minZoomDefault = 0.5f         //0.5f
        const val midZoomDefault = 2.0f
        const val maxZoomDefault = 10.0f
        const val partSizeDefault = 512f
        const val thumbnailRatioDefault = 0.45f
        const val goToPageGridColumnsDefault = 3
        const val inlineTextSelectionDefault = true
        const val detectExistingHighlightsDefault = true
        const val searchIgnoreAccentsDefault = false
        const val defaultTextModeDefault = false
        const val turnPageByVolumeButtonsDefault = false
        const val showScrollHandlePageCountDefault = false
        const val showAppBarPageCountDefault = false
        const val alwaysHideMarginsDefault = false
        const val secondBarEnabledDefault = false
        const val hideButtonsLabelsDefault = false
        const val fullScreenInfoShowTimeDefault = false
        const val fullScreenInfoShowPdfNameDefault = false
        const val fullScreenInfoShowPageNumberDefault = true
        const val fullScreenInfoShowReadingPercentageDefault = true
        const val doubleTapToExitEnabledDefault = false
        const val alwaysOpenAtFirstPageDefault = false
        const val autoFullScreenDefault = false
        const val alwaysHorizontalDefault = false
        const val scrollSpeedDefault = 3
        const val listFilterDefault = "RECENT"  // ListFilter.RECENT.name
        const val homeDisabledDefault = false
        const val homeShowPdfTitleDefault = true
        const val homeTabDefault = "RECENT"
        const val homeFolderFlatDefault = false
        const val homeViewModeDefault = "GRID"
        const val homeGridSizeDefault = "MEDIUM"
        const val homeSortDefault = "LAST_OPENED"
        const val themeSystem = "system"
        const val themeLight = "light"
        const val themeDark = "dark"
        val fullScreenOverlayActionsDefault = ConfigurableAction.defaultFullScreenOverlayActionIds
        val shortcutBarActionsDefault = ConfigurableAction.defaultShortcutBarActionIds

        // Colors
        const val pdfDarkBackgroundColor = -0x313132          // -0x313132 = 0xffcecece
        const val pdfLightBackgroundColor = -0xcdcdce         // 0xff323232 = -0xcdcdce

        // Constants
        const val minHighlightColors = 2
        const val maxHighlightColors = 4
        const val minMaxZoom = 1f
        const val maxMaxZoom = 100f
        const val minPartSize = 5f
        const val maxPartSize = 1000f
        const val AUTO_SCROLL_UNIT = 0.1
    }

    // get values saved in Shared Preferences or return the default values
    fun getFirstInstall() = prefMan.getBoolean(firstInstallKey, firstInstallDefault)
    fun getShowFeaturesDialog() = prefMan.getBoolean(showFeaturesDialogKey, showFeaturesDialogDefault)
    fun getHighQuality() = prefMan.getBoolean(highQualityKey, highQualityDefault)
    fun getAntiAliasing() = prefMan.getBoolean(antiAliasingKey, antiAliasingDefault)
    fun getHorizontalScroll() = prefMan.getBoolean(horizontalScrollKey, horizontalScrollDefault)
    fun getPageSnap() = prefMan.getBoolean(pageSnapKey, pageSnapDefault)
    fun getPageFling() = prefMan.getBoolean(pageFlingKey, pageFlingDefault)
    fun getBrowserScrollMode() = prefMan.getBoolean(browserScrollModeKey, browserScrollModeDefault)
    fun getTurnPageByMouseButtons() = prefMan.getBoolean(turnPageByMouseButtonsKey, turnPageByMouseButtonsDefault)
    fun getPdfDarkTheme() = prefMan.getBoolean(pdfDarkThemeKey, pdfDarkThemeDefault)
    fun getAppFollowSystemTheme() = prefMan.getBoolean(appFollowSystemThemeKey, appFollowSystemThemeDefault)
    fun getPdfFollowSystemTheme() = prefMan.getBoolean(pdfFollowSystemThemeKey, pdfFollowSystemThemeDefault)
    fun getInterfaceTheme(): String {
        return prefMan.getString(interfaceThemeKey, null)
            ?: if (getAppFollowSystemTheme()) themeSystem else themeLight
    }
    fun getPdfPagesTheme(): String {
        return prefMan.getString(pdfPagesThemeKey, null)
            ?: if (getPdfFollowSystemTheme()) themeSystem else if (getPdfDarkTheme()) themeDark else themeLight
    }
    fun getScreenOn() = prefMan.getBoolean(screenOnKey, screenOnDefault)
    fun getSpaceBetweenPages() = prefMan.getBoolean(spaceBetweenPagesKey, spaceBetweenPagesDefault)
    fun getHideDelay() = prefMan.getInt(hideDelayKey, hideDelayDefault)
    fun getPartSize() = prefMan.getFloat(partSizeKey, partSizeDefault)
    fun getThumbnailRation() = prefMan.getFloat(thumbnailRatioKey, thumbnailRatioDefault)

    fun getGoToPageGridColumns() = prefMan.getInt(goToPageGridColumnsKey, goToPageGridColumnsDefault)
    fun getMaxZoom() = prefMan.getFloat(maxZoomKey, maxZoomDefault)
    fun getInlineTextSelection() = prefMan.getBoolean(inlineTextSelectionKey, inlineTextSelectionDefault)
    fun getDetectExistingHighlights() = prefMan.getBoolean(detectExistingHighlightsKey, detectExistingHighlightsDefault)
    fun getHighlightColors(): List<Int> {
        val stored = prefMan.getString(highlightColorsKey, null)
            ?.split(",")
            ?.mapNotNull(HighlightPalette::fromName)
            ?: HighlightPalette.defaultSelection
        val selection = if (stored.size in minHighlightColors..maxHighlightColors) {
            stored
        } else {
            HighlightPalette.defaultSelection
        }
        return selection.map { it.colorValue }
    }
    fun getSearchIgnoreAccents() = prefMan.getBoolean(searchIgnoreAccentsKey, searchIgnoreAccentsDefault)
    fun getDefaultTextMode() = prefMan.getBoolean(defaultTextModeKey, defaultTextModeDefault)
    fun getTurnPageByVolumeButtons() = prefMan.getBoolean(turnPageByVolumeButtonsKey, turnPageByVolumeButtonsDefault)
    fun getShowScrollHandlePageCount() = prefMan.getBoolean(showScrollHandlePageCountKey, showScrollHandlePageCountDefault)
    fun getShowAppBarPageCount() = prefMan.getBoolean(showAppBarPageCountKey, showAppBarPageCountDefault)
    fun getAlwaysHideMargins() = prefMan.getBoolean(alwaysHideMarginsKey, alwaysHideMarginsDefault)
    fun getSecondBarEnabled() = prefMan.getBoolean(secondBarEnabledKey, secondBarEnabledDefault)
    fun getHideButtonsLabels() = prefMan.getBoolean(hideButtonsLabelsKey, hideButtonsLabelsDefault)
    fun getFullScreenInfoShowTime() = prefMan.getBoolean(fullScreenInfoShowTimeKey, fullScreenInfoShowTimeDefault)
    fun getFullScreenInfoShowPdfName() = prefMan.getBoolean(fullScreenInfoShowPdfNameKey, fullScreenInfoShowPdfNameDefault)
    fun getFullScreenInfoShowPageNumber() = prefMan.getBoolean(fullScreenInfoShowPageNumberKey, fullScreenInfoShowPageNumberDefault)
    fun getFullScreenInfoShowReadingPercentage() = prefMan.getBoolean(fullScreenInfoShowReadingPercentageKey, fullScreenInfoShowReadingPercentageDefault)
    fun getDoubleTapToExitEnabled() = prefMan.getBoolean(doubleTapToExitEnabledKey, doubleTapToExitEnabledDefault)

    fun getAlwaysOpenAtFirstPage() = prefMan.getBoolean(alwaysOpenAtFirstPageKey, alwaysOpenAtFirstPageDefault)
    fun getAutoFullScreen() = prefMan.getBoolean(autoFullScreenKey, autoFullScreenDefault)
    fun getAlwaysHorizontal() = prefMan.getBoolean(alwaysHorizontalKey, alwaysHorizontalDefault)
    fun getEnableReloadButton() = prefMan.getBoolean(enableReloadButtonKey, enableReloadButtonDefault)
    fun getPrimaryButtonAction() = prefMan.getString(
        primaryButtonActionKey,
        primaryButtonActionDefault,
    ) ?: primaryButtonActionDefault
    fun getSecondaryButtonAction(): String {
        if (prefMan.contains(secondaryButtonActionKey)) {
            return prefMan.getString(secondaryButtonActionKey, secondaryButtonActionDefault) ?: secondaryButtonActionDefault
        }
        return if (getEnableReloadButton()) ConfigurableAction.RELOAD.id else secondaryButtonActionDefault
    }
    fun getFullScreenOverlayActions(): Set<String> {
        return prefMan.getStringSet(fullScreenOverlayActionsKey, fullScreenOverlayActionsDefault)?.toSet()
            ?: fullScreenOverlayActionsDefault
    }
    fun getFullScreenOverlayActionOrder(): List<String> {
        return prefMan.getString(fullScreenOverlayActionOrderKey, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: ConfigurableAction.defaultFullScreenOverlayOrder.map { it.id }
    }
    fun getShortcutBarActions(): Set<String> {
        return prefMan.getStringSet(shortcutBarActionsKey, shortcutBarActionsDefault)?.toSet()
            ?: shortcutBarActionsDefault
    }
    fun getShortcutBarActionOrder(): List<String> {
        return prefMan.getString(shortcutBarActionOrderKey, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: ConfigurableAction.defaultShortcutBarOrder.map { it.id }
    }
    fun getScrollSpeed() = prefMan.getInt(scrollSpeedKey, scrollSpeedDefault)
    fun getListFilter() = ListFilter.valueOf(prefMan.getString(listFilterKey, listFilterDefault) as String)
    fun getHomeDisabled() = prefMan.getBoolean(homeDisabledKey, homeDisabledDefault)
    fun getHomeShowPdfTitle() = prefMan.getBoolean(homeShowPdfTitleKey, homeShowPdfTitleDefault)
    fun getHomeTab() = HomeTab.valueOf(prefMan.getString(homeTabKey, homeTabDefault) as String)
    fun getHomeFolderFlat() = prefMan.getBoolean(homeFolderFlatKey, homeFolderFlatDefault)
    fun getHomeViewMode() = HomeViewMode.valueOf(prefMan.getString(homeViewModeKey, homeViewModeDefault) as String)
    fun getHomeGridSize() = HomeGridSize.valueOf(prefMan.getString(homeGridSizeKey, homeGridSizeDefault) as String)
    fun getHomeSort() = HomeSortOrder.valueOf(prefMan.getString(homeSortKey, homeSortDefault) as String)

    // put values in Shared Preferences
    fun setFirstInstall(value: Boolean) = prefMan.edit().putBoolean(firstInstallKey, value).apply()
    fun setShowFeaturesDialog(value: Boolean) = prefMan.edit().putBoolean(showFeaturesDialogKey, value).apply()
    fun setHighQuality(value: Boolean) = prefMan.edit().putBoolean(highQualityKey, value).apply()
    fun setAntiAliasing(value: Boolean) = prefMan.edit().putBoolean(antiAliasingKey, value).apply()
    fun setHorizontalScroll(value: Boolean) = prefMan.edit().putBoolean(horizontalScrollKey, value).apply()
    fun setPageSnap(value: Boolean) = prefMan.edit().putBoolean(pageSnapKey, value).apply()
    fun setPageFling(value: Boolean) = prefMan.edit().putBoolean(pageFlingKey, value).apply()
    fun setBrowserScrollMode(value: Boolean) = prefMan.edit().putBoolean(browserScrollModeKey, value).apply()
    fun setTurnPageByMouseButtons(value: Boolean) = prefMan.edit().putBoolean(turnPageByMouseButtonsKey, value).apply()
    fun setPdfDarkTheme(value: Boolean) = prefMan.edit().putBoolean(pdfDarkThemeKey, value).apply()
    fun setAppFollowSystemTheme(value: Boolean) = prefMan.edit().putBoolean(appFollowSystemThemeKey, value).apply()
    fun setPdfFollowSystemTheme(value: Boolean) = prefMan.edit().putBoolean(pdfFollowSystemThemeKey, value).apply()
    fun setInterfaceTheme(value: String) = prefMan.edit()
        .putString(interfaceThemeKey, value)
        .putBoolean(appFollowSystemThemeKey, value == themeSystem)
        .apply()
    fun setPdfPagesTheme(value: String) = prefMan.edit()
        .putString(pdfPagesThemeKey, value)
        .putBoolean(pdfFollowSystemThemeKey, value == themeSystem)
        .putBoolean(pdfDarkThemeKey, value == themeDark)
        .apply()
    fun setScreenOn(value: Boolean) = prefMan.edit().putBoolean(screenOnKey, value).apply()
    fun setSpaceBetweenPages(value: Boolean) = prefMan.edit().putBoolean(spaceBetweenPagesKey, value).apply()
    fun setHideDelay(value: Int) = prefMan.edit().putInt(hideDelayKey, value).apply()
    fun setPartSize(value: Float) = prefMan.edit().putFloat(partSizeKey, value).apply()
    fun setThumbnailRatio(value: Float) = prefMan.edit().putFloat(thumbnailRatioKey, value).apply()

    fun setGoToPageGridColumns(value: Int) = prefMan.edit().putInt(goToPageGridColumnsKey, value).apply()
    fun setMaxZoom(value: Float) = prefMan.edit().putFloat(maxZoomKey, value).apply()
    fun setInlineTextSelection(value: Boolean) = prefMan.edit().putBoolean(inlineTextSelectionKey, value).apply()
    fun setDetectExistingHighlights(value: Boolean) = prefMan.edit().putBoolean(detectExistingHighlightsKey, value).apply()
    fun setHighlightColors(value: List<HighlightPalette>) = prefMan.edit()
        .putString(highlightColorsKey, value.joinToString(",") { it.name })
        .apply()
    fun setSearchIgnoreAccents(value: Boolean) = prefMan.edit().putBoolean(searchIgnoreAccentsKey, value).apply()
    fun setDefaultTextMode(value: Boolean) = prefMan.edit().putBoolean(defaultTextModeKey, value).apply()
    fun setTurnPageByVolumeButtons(value: Boolean) = prefMan.edit().putBoolean(turnPageByVolumeButtonsKey, value).apply()
    fun setShowScrollHandlePageCount(value: Boolean) = prefMan.edit().putBoolean(showScrollHandlePageCountKey, value).apply()
    fun setShowAppBarPageCount(value: Boolean) = prefMan.edit().putBoolean(showAppBarPageCountKey, value).apply()
    fun setAlwaysHideMargins(value: Boolean) = prefMan.edit().putBoolean(alwaysHideMarginsKey, value).apply()
    fun setSecondBarEnabled(value: Boolean) = prefMan.edit().putBoolean(secondBarEnabledKey, value).apply()
    fun setDoubleTapToExitEnabled(value: Boolean) = prefMan.edit().putBoolean(doubleTapToExitEnabledKey, value).apply()
    fun setAutoFullScreen(value: Boolean) = prefMan.edit().putBoolean(autoFullScreenKey, value).apply()
    fun setAlwaysHorizontal(value: Boolean) = prefMan.edit().putBoolean(alwaysHorizontalKey, value).apply()
    fun setHideButtonsLabels(value: Boolean) = prefMan.edit().putBoolean(hideButtonsLabelsKey, value).apply()
    fun setFullScreenInfoShowTime(value: Boolean) = prefMan.edit().putBoolean(fullScreenInfoShowTimeKey, value).apply()
    fun setFullScreenInfoShowPdfName(value: Boolean) = prefMan.edit().putBoolean(fullScreenInfoShowPdfNameKey, value).apply()
    fun setFullScreenInfoShowPageNumber(value: Boolean) = prefMan.edit().putBoolean(fullScreenInfoShowPageNumberKey, value).apply()
    fun setFullScreenInfoShowReadingPercentage(value: Boolean) = prefMan.edit().putBoolean(fullScreenInfoShowReadingPercentageKey, value).apply()
    fun setEnableReloadButton(value: Boolean) = prefMan.edit().putBoolean(enableReloadButtonKey, value).apply()
    fun setPrimaryButtonAction(value: String) = prefMan.edit().putString(primaryButtonActionKey, value).apply()
    fun setSecondaryButtonAction(value: String) = prefMan.edit().putString(secondaryButtonActionKey, value).apply()
    fun setFullScreenOverlayActions(value: Set<String>) = prefMan.edit().putStringSet(fullScreenOverlayActionsKey, value).apply()
    fun setFullScreenOverlayActionOrder(value: List<String>) = prefMan.edit()
        .putString(fullScreenOverlayActionOrderKey, value.joinToString(","))
        .apply()
    fun setShortcutBarActions(value: Set<String>) = prefMan.edit().putStringSet(shortcutBarActionsKey, value).apply()
    fun setShortcutBarActionOrder(value: List<String>) = prefMan.edit()
        .putString(shortcutBarActionOrderKey, value.joinToString(","))
        .apply()
    fun setScrollSpeed(value: Int) = prefMan.edit().putInt(scrollSpeedKey, value).apply()
    fun setListFilter(value: ListFilter) = prefMan.edit().putString(listFilterKey, value.name).apply()
    fun setHomeDisabled(value: Boolean) = prefMan.edit().putBoolean(homeDisabledKey, value).apply()
    fun setHomeShowPdfTitle(value: Boolean) = prefMan.edit().putBoolean(homeShowPdfTitleKey, value).apply()
    fun setHomeTab(value: HomeTab) = prefMan.edit().putString(homeTabKey, value.name).apply()
    fun setHomeFolderFlat(value: Boolean) = prefMan.edit().putBoolean(homeFolderFlatKey, value).apply()
    fun setHomeViewMode(value: HomeViewMode) = prefMan.edit().putString(homeViewModeKey, value.name).apply()
    fun setHomeGridSize(value: HomeGridSize) = prefMan.edit().putString(homeGridSizeKey, value.name).apply()
    fun setHomeSort(value: HomeSortOrder) = prefMan.edit().putString(homeSortKey, value.name).apply()

}
