package com.gitlab.mudlej.MjPdfReader.ui.main

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import com.gitlab.mudlej.MjPdfReader.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

private const val COLOR_FALLBACK = 0
private const val ICON_TEXT_PADDING_DP = 10
private const val ROW_VERTICAL_PADDING_DP = 14
private const val READER_ACTION_ROW_LAYOUT = android.R.layout.select_dialog_item
private const val READER_ACTION_TEXT_ID = android.R.id.text1

data class ReaderAction(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val visible: Boolean = true,
    val onClick: () -> Unit,
)

fun showReaderActionsDialog(activity: MainActivity, actions: List<ReaderAction>) {
    val rowStyle = createReaderActionRowStyle(activity)
    val rows = actions.toVisibleRows(activity, rowStyle.textColor)
    val adapter = ReaderActionRowsAdapter(activity, rows, rowStyle)

    MaterialAlertDialogBuilder(activity)
        .setAdapter(adapter) { dialog, item ->
            dialog.dismiss()
            rows[item].onClick()
        }
        .show()
}

private fun createReaderActionRowStyle(activity: MainActivity): ReaderActionRowStyle {
    val density = activity.resources.displayMetrics.density
    return ReaderActionRowStyle(
        textColor = MaterialColors.getColor(activity, R.attr.colorOnSurface, COLOR_FALLBACK),
        iconTextPadding = dpToPx(ICON_TEXT_PADDING_DP, density),
        verticalPadding = dpToPx(ROW_VERTICAL_PADDING_DP, density),
    )
}

private fun List<ReaderAction>.toVisibleRows(
    activity: MainActivity,
    iconTint: Int,
): List<ReaderActionRow> {
    return filter { it.visible }.map { action ->
        ReaderActionRow(
            title = activity.getString(action.titleRes),
            icon = action.loadTintedIcon(activity, iconTint),
            onClick = action.onClick,
        )
    }
}

private fun ReaderAction.loadTintedIcon(activity: MainActivity, tint: Int): Drawable? {
    return AppCompatResources
        .getDrawable(activity, iconRes)
        ?.mutate()
        ?.apply { setTint(tint) }
}

private class ReaderActionRowsAdapter(
    activity: MainActivity,
    private val rows: List<ReaderActionRow>,
    private val rowStyle: ReaderActionRowStyle,
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(activity)

    override fun getCount() = rows.size

    override fun getItem(position: Int) = rows[position]

    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(READER_ACTION_ROW_LAYOUT, parent, false)
        view.bindReaderActionRow(rows[position], rowStyle)
        return view
    }
}

private fun View.bindReaderActionRow(row: ReaderActionRow, rowStyle: ReaderActionRowStyle) {
    val textView = findViewById<TextView>(READER_ACTION_TEXT_ID) ?: this as TextView
    minimumHeight = 0

    textView.setTextColor(rowStyle.textColor)
    textView.setCompoundDrawablesWithIntrinsicBounds(row.icon, null, null, null)
    textView.text = row.title
    textView.compoundDrawablePadding = rowStyle.iconTextPadding
    textView.minHeight = 0
    textView.setPadding(
        textView.paddingLeft,
        rowStyle.verticalPadding,
        textView.paddingRight,
        rowStyle.verticalPadding
    )
}

private fun dpToPx(dp: Int, density: Float): Int {
    return (dp * density + 0.5f).toInt()
}

private data class ReaderActionRowStyle(
    val textColor: Int,
    val iconTextPadding: Int,
    val verticalPadding: Int,
)

private data class ReaderActionRow(
    val title: String,
    val icon: Drawable?,
    val onClick: () -> Unit,
)
