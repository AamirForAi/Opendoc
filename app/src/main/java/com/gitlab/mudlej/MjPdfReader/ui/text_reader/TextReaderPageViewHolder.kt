package com.gitlab.mudlej.MjPdfReader.ui.text_reader

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.Selection
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.TextReaderPageItemBinding
import com.gitlab.mudlej.MjPdfReader.util.plainTextShareIntent

class TextReaderPageViewHolder(
    private val binding: TextReaderPageItemBinding,
    private val onRetry: (Int) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(state: TextReaderPageState, settings: TextReaderSettings) {
        val context = binding.root.context
        val colors = settings.theme.colors(binding.root)
        val horizontalPadding = dp(settings.horizontalMargin)

        binding.root.setBackgroundColor(colors.background)
        binding.pageContainer.setPadding(horizontalPadding, dp(18), horizontalPadding, dp(18))
        binding.pageLabel.text = context.getString(R.string.text_reader_page_label, state.pageIndex + 1)
        binding.pageLabel.setTextColor(colors.label)
        binding.pageMessage.setTextColor(colors.label)
        binding.pageText.setTextColor(colors.text)
        binding.pageText.textSize = settings.fontSize
        binding.pageText.typeface = settings.fontFamily.typeface()
        binding.pageText.setLineSpacing(0f, settings.lineSpacing)
        binding.pageText.customSelectionActionModeCallback = selectionActionModeCallback()

        binding.pageProgressBar.visibility = View.GONE
        binding.pageMessage.visibility = View.GONE
        binding.pageText.visibility = View.GONE
        binding.pageMessage.setOnClickListener(null)

        when (state) {
            is TextReaderPageState.NotLoaded,
            is TextReaderPageState.Loading -> {
                binding.pageProgressBar.visibility = View.VISIBLE
                binding.pageMessage.visibility = View.VISIBLE
                binding.pageMessage.text = context.getString(R.string.text_reader_loading_page)
            }
            is TextReaderPageState.Ready -> {
                binding.pageText.visibility = View.VISIBLE
                binding.pageText.text = state.text
            }
            is TextReaderPageState.Empty -> {
                binding.pageMessage.visibility = View.VISIBLE
                binding.pageMessage.text = context.getString(R.string.text_reader_no_text)
            }
            is TextReaderPageState.Error -> {
                binding.pageMessage.visibility = View.VISIBLE
                binding.pageMessage.text = context.getString(
                    R.string.text_reader_failed_page,
                ) + " " + context.getString(R.string.text_reader_retry)
                binding.pageMessage.setOnClickListener { onRetry(state.pageIndex) }
            }
        }
    }

    private fun selectionActionModeCallback(): ActionMode.Callback {
        return object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(0, SHARE_SELECTION_ID, 10, R.string.share)
                menu.add(0, SEARCH_WEB_SELECTION_ID, 11, R.string.search_web)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val selectedText = selectedText().takeIf { it.isNotBlank() } ?: return false
                when (item.itemId) {
                    SHARE_SELECTION_ID -> binding.root.context.startActivity(
                        plainTextShareIntent(binding.root.context.getString(R.string.share), selectedText)
                    )
                    SEARCH_WEB_SELECTION_ID -> searchWeb(selectedText)
                    else -> return false
                }
                mode.finish()
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) = Unit
        }
    }

    private fun selectedText(): String {
        val text = binding.pageText.text ?: return ""
        val start = Selection.getSelectionStart(text)
        val end = Selection.getSelectionEnd(text)
        if (start == -1 || end == -1 || start == end) return ""

        return text.substring(start.coerceAtMost(end), start.coerceAtLeast(end))
    }

    private fun searchWeb(text: String) {
        val context = binding.root.context
        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text)
        try {
            context.startActivity(searchIntent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(text)}")))
        }
    }

    private fun dp(value: Int): Int {
        return (value * binding.root.resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val SHARE_SELECTION_ID = 1001
        const val SEARCH_WEB_SELECTION_ID = 1002
    }
}
