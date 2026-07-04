package com.gitlab.mudlej.MjPdfReader.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.enums.ConfigurableAction
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showFullScreenButtonsPreferenceDialog(
    context: Context,
    preferences: Preferences,
    onSaved: () -> Unit,
) {
    val rows = fullScreenButtonRows(preferences)
    val adapter = FullScreenButtonsAdapter(rows)
    val recyclerView = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        this.adapter = adapter
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    }
    val touchHelper = ItemTouchHelper(FullScreenButtonTouchCallback(adapter))
    adapter.onDragRequested = touchHelper::startDrag
    touchHelper.attachToRecyclerView(recyclerView)

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.fullscreen_buttons)
        .setView(recyclerView)
        .setPositiveButton(R.string.apply) { _, _ ->
            preferences.setFullScreenOverlayActions(rows.enabledActionIds())
            preferences.setFullScreenOverlayActionOrder(rows.map { it.action.id })
            onSaved()
        }
        .setNegativeButton(R.string.cancel, null)
        .setNeutralButton(R.string.reset, null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            rows.resetToDefaults()
            adapter.notifyDataSetChanged()
            recyclerView.scrollToPosition(0)
        }
    }
    dialog.show()
}

fun fullScreenButtonsSummary(context: Context, preferences: Preferences): String {
    val selectedIds = preferences.getFullScreenOverlayActions()
    val selectedTitles = preferences.getFullScreenOverlayActionOrder()
        .map(ConfigurableAction::fromId)
        .filter { ConfigurableAction.fullScreenOverlayActions.contains(it) && selectedIds.contains(it.id) }
        .map { context.getString(it.titleRes) }
    return (listOf(context.getString(R.string.exit)) + selectedTitles).joinToString(", ")
}

private fun fullScreenButtonRows(preferences: Preferences): MutableList<FullScreenButtonRow> {
    val selectedIds = preferences.getFullScreenOverlayActions()
    val orderedActions = (preferences.getFullScreenOverlayActionOrder().map(ConfigurableAction::fromId)
        + ConfigurableAction.defaultFullScreenOverlayOrder)
        .distinct()
        .filter { it.isFullScreenButtonOption() }
    return orderedActions.map { action ->
        FullScreenButtonRow(
            action,
            enabled = selectedIds.contains(action.id),
            locked = ConfigurableAction.requiredFullScreenOverlayActionIds.contains(action.id),
        )
    }.toMutableList()
}

private fun defaultFullScreenButtonRows(): List<FullScreenButtonRow> {
    return ConfigurableAction.defaultFullScreenOverlayOrder.map { action ->
        FullScreenButtonRow(
            action,
            enabled = ConfigurableAction.defaultFullScreenOverlayActionIds.contains(action.id),
            locked = ConfigurableAction.requiredFullScreenOverlayActionIds.contains(action.id),
        )
    }
}

private fun MutableList<FullScreenButtonRow>.resetToDefaults() {
    clear()
    addAll(defaultFullScreenButtonRows())
}

private fun List<FullScreenButtonRow>.enabledActionIds(): Set<String> {
    return filter { it.enabled }.map { it.action.id }.toSet()
}

private fun ConfigurableAction.isFullScreenButtonOption(): Boolean {
    return this == ConfigurableAction.EXIT_FULLSCREEN || ConfigurableAction.fullScreenOverlayActions.contains(this)
}

private data class FullScreenButtonRow(
    val action: ConfigurableAction,
    var enabled: Boolean,
    val locked: Boolean,
)

private class FullScreenButtonsAdapter(
    private val rows: MutableList<FullScreenButtonRow>,
) : RecyclerView.Adapter<FullScreenButtonViewHolder>() {

    var onDragRequested: ((RecyclerView.ViewHolder) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FullScreenButtonViewHolder {
        return FullScreenButtonViewHolder(parent.createRowView())
    }

    override fun onBindViewHolder(holder: FullScreenButtonViewHolder, position: Int) {
        holder.bind(rows[position], onDragRequested)
    }

    override fun getItemCount() = rows.size

    fun move(from: Int, to: Int): Boolean {
        if (from !in rows.indices || to !in rows.indices) {
            return false
        }
        if (rows[from].locked || rows[to].locked) {
            return false
        }
        rows.add(to, rows.removeAt(from))
        notifyItemMoved(from, to)
        return true
    }
}

private class FullScreenButtonViewHolder(
    view: View,
) : RecyclerView.ViewHolder(view) {

    private val checkbox = view.findViewWithTag<MaterialCheckBox>(CHECKBOX_TAG)
    private val title = view.findViewWithTag<TextView>(TITLE_TAG)
    private val dragHandle = view.findViewWithTag<AppCompatImageView>(DRAG_HANDLE_TAG)

    @SuppressLint("ClickableViewAccessibility")
    fun bind(
        row: FullScreenButtonRow,
        onDragRequested: ((RecyclerView.ViewHolder) -> Unit)?,
    ) {
        checkbox.setOnCheckedChangeListener(null)
        checkbox.isChecked = row.enabled || row.locked
        checkbox.isEnabled = !row.locked
        checkbox.setOnCheckedChangeListener { _, isChecked -> row.enabled = isChecked }
        title.text = itemView.context.getString(row.action.titleRes)
        dragHandle.visibility = if (row.locked) View.INVISIBLE else View.VISIBLE
        dragHandle.setOnTouchListener(
            if (row.locked) {
                null
            }
            else {
                View.OnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onDragRequested?.invoke(this)
                    }
                    false
                }
            }
        )
    }
}

private class FullScreenButtonTouchCallback(
    private val adapter: FullScreenButtonsAdapter,
) : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        return adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
}

private fun ViewGroup.createRowView(): View {
    val context = context
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(8))
        addView(createDragHandle(context).apply { tag = DRAG_HANDLE_TAG })
        addView(MaterialCheckBox(context).apply { tag = CHECKBOX_TAG })
        addView(TextView(context).apply {
            tag = TITLE_TAG
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
    }
}

private fun createDragHandle(context: Context): AppCompatImageView {
    return AppCompatImageView(context).apply {
        contentDescription = context.getString(R.string.drag_to_reorder)
        setImageResource(R.drawable.ic_burger_menu)
        setColorFilter(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
    }
}

private fun Context.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}

private const val CHECKBOX_TAG = "checkbox"
private const val TITLE_TAG = "title"
private const val DRAG_HANDLE_TAG = "dragHandle"
