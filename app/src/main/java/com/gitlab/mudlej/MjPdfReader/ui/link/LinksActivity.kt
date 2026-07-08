package com.gitlab.mudlej.MjPdfReader.ui.link

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Link
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityLinkBinding
import com.gitlab.mudlej.MjPdfReader.manager.extractor.PdfExtractor
import com.gitlab.mudlej.MjPdfReader.util.ColorUtil
import com.gitlab.mudlej.MjPdfReader.util.configureSearchIcon
import com.gitlab.mudlej.MjPdfReader.util.copyToClipboard
import com.gitlab.mudlej.MjPdfReader.util.createPdfExtractor
import com.gitlab.mudlej.MjPdfReader.util.tintIconsForChrome
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.concurrent.thread

class LinksActivity : AppCompatActivity(), LinkFunctions {
    private lateinit var binding: ActivityLinkBinding
    private lateinit var pdfExtractor: PdfExtractor
    private val linkAdapter = LinkAdapter(this, this)
    private var links: List<Link> = listOf()
    private val lastPageLiveData = MutableLiveData<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLinkBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ColorUtil.colorize(this, window, supportActionBar)

        showProgressBar()

        lifecycleScope.launch {
            initPdfExtractor()
            if (::pdfExtractor.isInitialized) {
                initActionBar()
                initLinks()
                initUi()
            } else {
                finish()
            }
        }
    }

    private fun showProgressBar() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private suspend fun initPdfExtractor() {
        val pdfPath = intent.getStringExtra(PDF.filePathKey)
        val pdfPassword = intent.getStringExtra(PDF.passwordKey)
        try {
            pdfExtractor = withContext(Dispatchers.IO) {
                createPdfExtractor(this@LinksActivity, Uri.parse(pdfPath), pdfPassword)
            }
        }
        catch (throwable: Throwable) {
            Toast.makeText(
                this,
                "Failed to read links! (file move or deleted?)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroy() {
        if (::pdfExtractor.isInitialized) {
            val extractor = pdfExtractor
            thread { runCatching { extractor.close() } }
        }
        super.onDestroy()
    }

    private fun initLinks() {
        val pageCount = pdfExtractor.getPageCount()
        binding.progressBar.visibility = View.GONE
        binding.linksProgressBar.max = pageCount
        binding.linksProgressBar.progress = 0
        binding.linksProgressBar.visibility = View.VISIBLE
        lastPageLiveData.observe(this) { pageNumber ->
            binding.linksProgressBar.progress = pageNumber
        }

        lifecycleScope.launch(Dispatchers.Default) {
            val collected = mutableListOf<Link>()
            var lastSubmittedCount = 0

            for (pageIndex in 0 until pageCount) {
                yield()
                for (pageLink in pdfExtractor.getPageLinks(pageIndex)) {
                    val url = pageLink.uri
                    if (url.isNullOrBlank()) {
                        continue
                    }
                    collected.add(Link(text = "", url = url, pageNumber = pageIndex + 1))
                }
                lastPageLiveData.postValue(pageIndex + 1)

                val isBatchBoundary = pageIndex % LINKS_BATCH_PAGES == 0 || pageIndex == pageCount - 1
                if (isBatchBoundary && collected.size > lastSubmittedCount) {
                    lastSubmittedCount = collected.size
                    val snapshot = collected.toList()
                    withContext(Dispatchers.Main) {
                        links = snapshot
                        linkAdapter.submitList(snapshot)
                        binding.message.visibility = View.GONE
                    }
                }
            }

            withContext(Dispatchers.Main) {
                links = collected.toList()
                linkAdapter.submitList(links)
                binding.linksProgressBar.visibility = View.GONE
                postGettingLinks()
            }
        }
    }

    private fun postGettingLinks() {
        if (links.isNotEmpty()) {
            binding.message.visibility = View.GONE
        }
        else {
            binding.message.text = getString(R.string.no_links_put_in_pdf)
        }

        // set up the title in the App Bar
        title = "${"%,d".format(links.size)} ${getString(R.string.links_in_document)}"

        // show too many results message
        if (links.size > PDF.TOO_MANY_RESULTS) {
            Snackbar.make(binding.root,getString(R.string.too_many_results_may_be_slow), Snackbar.LENGTH_INDEFINITE).also {
                it.setAction(getText(R.string.ok)) { }
                it.show()
            }
        }
    }

    private fun initActionBar() {
        // add back button to the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        title = getString(R.string.loading)
    }

    private fun initUi() {
        title = getString(R.string.links_activity_title)
        linkAdapter.submitList(links)
        linkAdapter.progressBar = binding.progressBar
        binding.linkRecyclerView.apply {
            adapter = linkAdapter
            layoutManager = LinearLayoutManager(this@LinksActivity)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_menu, menu)
        menu.tintIconsForChrome(this)
        configureSearchIcon(menu, links.isNotEmpty())
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // set search functionality
        val searchView = menu.findItem(R.id.search_in_search_activity).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String) = false

            override fun onQueryTextChange(query: String): Boolean {
                linkAdapter.nestedQuery = query
                binding.progressBar.visibility = View.VISIBLE
                val filteredList = links.filter {
                    it.url.contains(query, true)  || it.text.contains(query, true)
                }
                linkAdapter.submitList(filteredList)
                linkAdapter.notifyDataSetChanged() // because the comparator doesn't see the difference in text style

                Snackbar.make(
                    binding.root,
                    getString(R.string.number_of_filtered_results).format(filteredList.size),
                    Snackbar.LENGTH_SHORT
                ).show()
                return false
            }
        })
        searchView.setOnCloseListener {
            linkAdapter.submitList(links)
            true
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onLinkClicked(link: Link) {
        Intent(Intent.ACTION_VIEW).also {
            it.data = (Uri.parse(link.url))
            try {
                startActivity(it)
            } catch (throwable: Throwable) {
                Snackbar.make(binding.root, getString(R.string.no_app_to_open_link), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onPageNumberClicked(link: Link) {
        Intent().also { resultIntent ->
            resultIntent.putExtra(PDF.linkResultKey, link.pageNumber)
            setResult(PDF.LINK_RESULT_OK, resultIntent)
        }
        finish()
    }

    override fun onCopyLinkClicked(link: Link) {
        val copyLabel = "Link URL copy"
        copyToClipboard(this, copyLabel, link.url)
    }

    companion object {
        const val TAG = "LinksActivity"
        private const val LINKS_BATCH_PAGES = 50
    }

}
