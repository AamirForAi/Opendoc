package com.gitlab.mudlej.MjPdfReader.ui.history

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityNavigationHistoryBinding
import com.gitlab.mudlej.MjPdfReader.ui.main.ReaderHistoryManager
import com.gitlab.mudlej.MjPdfReader.ui.toc.TocPathResolver
import com.gitlab.mudlej.MjPdfReader.util.ColorUtil
import kotlinx.coroutines.launch

class NavigationHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNavigationHistoryBinding
    private val historyAdapter = NavigationHistoryAdapter(::onHistoryEntryClicked)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavigationHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ColorUtil.colorize(this, window, supportActionBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.navigation_history)

        binding.navigationHistoryRecyclerView.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(this@NavigationHistoryActivity)
        }
        val entries = historyEntriesFromIntent()
        showEntries(entries)
        loadTocPaths(entries)
    }

    private fun loadTocPaths(entries: List<NavigationHistoryRow>) {
        if (entries.isEmpty()) {
            return
        }
        lifecycleScope.launch {
            val resolver = TocPathResolver.load(
                this@NavigationHistoryActivity,
                intent.getStringExtra(PDF.filePathKey),
                intent.getStringExtra(PDF.passwordKey),
            )
            if (resolver !== TocPathResolver.EMPTY) {
                showEntries(entries.map { it.copy(tocPath = resolver.resolve(it.pageIndex)) })
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showEntries(entries: List<NavigationHistoryRow>) {
        historyAdapter.submitList(entries)
        binding.message.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun historyEntriesFromIntent(): List<NavigationHistoryRow> {
        val pages = intent.getIntArrayExtra(EXTRA_PAGES) ?: return emptyList()
        val origins = intent.getStringArrayExtra(EXTRA_ORIGINS) ?: return emptyList()
        val timestamps = intent.getLongArrayExtra(EXTRA_TIMESTAMPS) ?: return emptyList()
        val backStackIndices = intent.getIntArrayExtra(EXTRA_BACK_STACK_INDICES) ?: return emptyList()
        if (pages.size != origins.size || pages.size != timestamps.size || pages.size != backStackIndices.size) {
            return emptyList()
        }

        return pages.indices.map { index ->
            val origin = ReaderHistoryManager.Origin.entries.firstOrNull { it.name == origins[index] }
                ?: ReaderHistoryManager.Origin.HISTORY
            NavigationHistoryRow(
                pageIndex = pages[index],
                origin = origin,
                timestamp = timestamps[index],
                backStackIndex = backStackIndices[index],
                tocPath = null,
            )
        }
    }

    private fun onHistoryEntryClicked(entry: NavigationHistoryRow) {
        val resultIntent = Intent().putExtra(EXTRA_SELECTED_BACK_STACK_INDEX, entry.backStackIndex)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        const val EXTRA_SELECTED_BACK_STACK_INDEX = "selectedBackStackIndex"

        private const val EXTRA_PAGES = "pages"
        private const val EXTRA_ORIGINS = "origins"
        private const val EXTRA_TIMESTAMPS = "timestamps"
        private const val EXTRA_BACK_STACK_INDICES = "backStackIndices"

        fun createIntent(context: Context, entries: List<ReaderHistoryManager.Entry>): Intent {
            val rows = entries.withIndex().toList().asReversed()
            return Intent(context, NavigationHistoryActivity::class.java).apply {
                putExtra(EXTRA_PAGES, rows.map { it.value.pageIndex }.toIntArray())
                putExtra(EXTRA_ORIGINS, rows.map { it.value.origin.name }.toTypedArray())
                putExtra(EXTRA_TIMESTAMPS, rows.map { it.value.timestamp }.toLongArray())
                putExtra(EXTRA_BACK_STACK_INDICES, rows.map { it.index }.toIntArray())
            }
        }
    }
}

data class NavigationHistoryRow(
    val pageIndex: Int,
    val origin: ReaderHistoryManager.Origin,
    val timestamp: Long,
    val backStackIndex: Int,
    val tocPath: String?,
)
