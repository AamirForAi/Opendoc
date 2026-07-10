package com.gitlab.mudlej.MjPdfReader.ui.bookmark

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityUserBookmarksBinding
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManagerImpl
import com.gitlab.mudlej.MjPdfReader.repository.AppDatabase
import com.gitlab.mudlej.MjPdfReader.repository.UserBookmark
import com.gitlab.mudlej.MjPdfReader.util.ColorUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class UserBookmarksActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserBookmarksBinding
    private val databaseManager by lazy { DatabaseManagerImpl(AppDatabase.getInstance(applicationContext)) }
    private val bookmarkAdapter = UserBookmarkAdapter(
        ::onBookmarkClicked,
        ::deleteBookmark,
        ::showRenameDialog,
    )
    private var bookmarks: List<UserBookmark> = listOf()
    private var fileHash: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserBookmarksBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ColorUtil.colorize(this, window, supportActionBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.bookmarks)

        fileHash = intent.getStringExtra(PDF.fileHashKey)
        binding.userBookmarksRecyclerView.apply {
            adapter = bookmarkAdapter
            layoutManager = LinearLayoutManager(this@UserBookmarksActivity)
        }
        loadBookmarks()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadBookmarks() {
        val hash = fileHash
        if (hash == null) {
            showBookmarks(emptyList())
            return
        }
        lifecycleScope.launch {
            showBookmarks(databaseManager.findUserBookmarks(hash))
        }
    }

    private fun showBookmarks(loadedBookmarks: List<UserBookmark>) {
        bookmarks = loadedBookmarks
        bookmarkAdapter.submitList(bookmarks)
        binding.message.visibility = if (bookmarks.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onBookmarkClicked(bookmark: UserBookmark) {
        val resultIntent = Intent()
        resultIntent.putExtra(PDF.chosenBookmarkKey, bookmark.pageIndex)
        setResult(PDF.BOOKMARK_RESULT_OK, resultIntent)
        finish()
    }

    private fun deleteBookmark(bookmark: UserBookmark) {
        lifecycleScope.launch {
            databaseManager.removeUserBookmark(bookmark.fileHash, bookmark.pageIndex)
            showBookmarks(bookmarks.filterNot { it.pageIndex == bookmark.pageIndex })
        }
    }

    private fun showRenameDialog(bookmark: UserBookmark) {
        val editText = EditText(this).apply {
            setText(bookmark.label.orEmpty())
            hint = getString(R.string.bookmark_page_label, bookmark.pageIndex + 1)
            setSelection(text.length)
        }
        val container = FrameLayout(this).apply {
            val horizontalPadding = (20 * resources.displayMetrics.density).toInt()
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            addView(editText)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename_bookmark)
            .setView(container)
            .setPositiveButton(R.string.apply) { _, _ ->
                val label = editText.text.toString().trim().takeUnless { it.isBlank() }
                lifecycleScope.launch {
                    databaseManager.setUserBookmarkLabel(bookmark.fileHash, bookmark.pageIndex, label)
                    showBookmarks(
                        bookmarks.map {
                            if (it.pageIndex == bookmark.pageIndex) it.copy(label = label) else it
                        }
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
