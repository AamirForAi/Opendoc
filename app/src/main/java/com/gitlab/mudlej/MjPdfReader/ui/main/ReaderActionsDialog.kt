package com.gitlab.mudlej.MjPdfReader.ui.main

import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListAdapter
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import com.gitlab.mudlej.MjPdfReader.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

data class ReaderAction(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val visible: Boolean = true,
    val onClick: () -> Unit,
)

fun showReaderActionsDialog(activity: MainActivity, actions: List<ReaderAction>) {
    val visibleActions = actions.filter { it.visible }
    val adapter: ListAdapter = object : ArrayAdapter<ReaderAction>(
        activity,
        android.R.layout.select_dialog_item,
        android.R.id.text1,
        visibleActions
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val textView = view.findViewById(android.R.id.text1) as TextView
            val action = visibleActions[position]
            val itemColor = MaterialColors.getColor(parent, R.attr.colorOnSurface)
            val icon = AppCompatResources
                .getDrawable(activity, action.iconRes)
                ?.mutate()

            icon?.setTint(itemColor)
            textView.setTextColor(itemColor)
            textView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
            textView.text = activity.getString(action.titleRes)
            textView.compoundDrawablePadding = (10 * activity.resources.displayMetrics.density + 0.5f).toInt()
            return view
        }
    }

    MaterialAlertDialogBuilder(activity)
        .setAdapter(adapter) { dialog, item ->
            dialog.dismiss()
            visibleActions[item].onClick()
        }
        .show()
}
