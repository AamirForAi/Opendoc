package com.gitlab.mudlej.MjPdfReader.ui.settings

import android.content.Context
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.enums.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.enums.orderedFullScreenOverlayActionIds
import com.gitlab.mudlej.MjPdfReader.enums.orderedFullScreenOverlayActions
import com.gitlab.mudlej.MjPdfReader.enums.orderedSelectedFullScreenOverlayActions
import com.gitlab.mudlej.MjPdfReader.enums.selectedFullScreenOverlayActionIds

fun showFullScreenButtonsPreferenceDialog(
    context: Context,
    preferences: Preferences,
    onSaved: () -> Unit,
) {
    val rows = fullScreenButtonRows(preferences)
    showActionSelectionPreferenceDialog(
        context = context,
        titleRes = R.string.fullscreen_buttons,
        rows = rows,
        defaultRows = defaultFullScreenButtonRows(),
    ) {
        preferences.setFullScreenOverlayActions(selectedFullScreenOverlayActionIds(rows.enabledActionIds()))
        preferences.setFullScreenOverlayActionOrder(orderedFullScreenOverlayActionIds(rows.map { it.action.id }))
        onSaved()
    }
}

fun fullScreenButtonsSummary(context: Context, preferences: Preferences): String {
    val selectedTitles = orderedSelectedFullScreenOverlayActions(
        selectedIds = preferences.getFullScreenOverlayActions(),
        actionOrder = preferences.getFullScreenOverlayActionOrder(),
    )
        .filter { it != ConfigurableAction.EXIT_FULLSCREEN }
        .map { context.getString(it.titleRes) }
    return (listOf(context.getString(R.string.exit)) + selectedTitles).joinToString(", ")
}

private fun fullScreenButtonRows(preferences: Preferences): MutableList<ActionSelectionRow> {
    val selectedIds = selectedFullScreenOverlayActionIds(preferences.getFullScreenOverlayActions())
    val orderedActions = orderedFullScreenOverlayActions(preferences.getFullScreenOverlayActionOrder())
    return orderedActions.map { action ->
        ActionSelectionRow(
            action,
            enabled = selectedIds.contains(action.id),
            locked = ConfigurableAction.requiredFullScreenOverlayActionIds.contains(action.id),
        )
    }.toMutableList()
}

private fun defaultFullScreenButtonRows(): List<ActionSelectionRow> {
    return ConfigurableAction.defaultFullScreenOverlayOrder.map { action ->
        ActionSelectionRow(
            action,
            enabled = ConfigurableAction.defaultFullScreenOverlayActionIds.contains(action.id),
            locked = ConfigurableAction.requiredFullScreenOverlayActionIds.contains(action.id),
        )
    }
}
