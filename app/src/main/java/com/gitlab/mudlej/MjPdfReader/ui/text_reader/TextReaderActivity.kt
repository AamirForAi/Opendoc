package com.gitlab.mudlej.MjPdfReader.ui.text_reader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.view.doOnNextLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityTextReaderBinding
import com.gitlab.mudlej.MjPdfReader.databinding.TextReaderTypographySheetBinding
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManager
import com.gitlab.mudlej.MjPdfReader.manager.database.DatabaseManagerImpl
import com.gitlab.mudlej.MjPdfReader.manager.extractor.PdfExtractor
import com.gitlab.mudlej.MjPdfReader.repository.AppDatabase
import com.gitlab.mudlej.MjPdfReader.ui.bookmark.BookmarkState
import com.gitlab.mudlej.MjPdfReader.ui.bookmark.BookmarksActivity
import com.gitlab.mudlej.MjPdfReader.ui.showGoToPageDialog
import com.gitlab.mudlej.MjPdfReader.util.ColorUtil
import com.gitlab.mudlej.MjPdfReader.util.computeHash
import com.gitlab.mudlej.MjPdfReader.util.createPdfExtractor
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections

class TextReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextReaderBinding
    private lateinit var adapter: TextReaderPageAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var pdfExtractor: PdfExtractor
    private lateinit var databaseManager: DatabaseManager
    private lateinit var pdfUri: Uri

    private var pendingExtractor: PdfExtractor? = null
    private var pdfPassword: String? = null
    private var fileHash: String? = null
    private var pageCount = 0
    private var currentPageIndex = 0
    private var resultPrepared = false
    @Volatile
    private var isClosing = false
    private var settings = TextReaderSettings()
    private var bookmarkState = BookmarkState()
    private var savedPageIndex = -1

    private val extractionMutex = Mutex()
    private val extractionJobs = Collections.synchronizedSet(mutableSetOf<Job>())
    private var setupJob: Job? = null
    private val loadingPages = mutableSetOf<Int>()
    private val textCache = LinkedHashMap<Int, String>(CACHE_PAGE_LIMIT, 0.75f, true)
    private val controlsHideHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideReaderControls() }
    private val loadVisiblePagesRunnable = Runnable {
        if (::binding.isInitialized && ::layoutManager.isInitialized) {
            loadVisiblePages()
        }
    }
    private var controlsHideDelayMillis = Preferences.hideDelayDefault.toLong()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ColorUtil.colorize(this, window, supportActionBar)

        databaseManager = DatabaseManagerImpl(AppDatabase.getInstance(applicationContext))
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        settings = TextReaderSettings.load(sharedPreferences)
        controlsHideDelayMillis = Preferences(sharedPreferences).getHideDelay().toLong() + CONTROLS_EXTRA_HIDE_DELAY_MS
        restoreState(savedInstanceState)
        initPdfProperties()
        if (!::pdfUri.isInitialized) return

        setupJob = lifecycleScope.launch {
            showLoading()
            val extractor = createExtractor()
            if (extractor == null) {
                badFileExit()
                return@launch
            }
            if (!initializeExtractor(extractor)) {
                return@launch
            }
            if (pageCount <= 0) {
                badFileExit()
                return@launch
            }
            if (fileHash == null) {
                fileHash = computeHash(this@TextReaderActivity, PDF(uri = pdfUri))
            }
            currentPageIndex = currentPageIndex.coerceIn(0, pageCount - 1)
            initReader()
            hideLoading()
        }
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        bookmarkState = savedInstanceState?.let { BookmarkState.from(it) } ?: BookmarkState.from(intent)
        currentPageIndex = savedInstanceState?.getInt(CURRENT_PAGE_KEY)
            ?: intent.getIntExtra(PDF.pageNumberKey, 0)
        fileHash = savedInstanceState?.getString(PDF.fileHashKey)
            ?: intent.getStringExtra(PDF.fileHashKey)
    }

    private fun initPdfProperties() {
        val pdfPath = intent.getStringExtra(PDF.filePathKey)
        if (pdfPath.isNullOrBlank()) {
            badFileExit()
            return
        }

        pdfUri = Uri.parse(pdfPath)
        pdfPassword = intent.getStringExtra(PDF.passwordKey)
    }

    private suspend fun createExtractor(): PdfExtractor? {
        return withContext(Dispatchers.IO) {
            extractionMutex.withLock {
                try {
                    createPdfExtractor(this@TextReaderActivity, pdfUri, pdfPassword).also { extractor ->
                        if (isClosing) {
                            extractor.close()
                        } else {
                            pendingExtractor = extractor
                        }
                    }
                } catch (throwable: Throwable) {
                    null
                }
            }
        }
    }

    private suspend fun initializeExtractor(extractor: PdfExtractor): Boolean {
        return withContext(Dispatchers.IO) {
            extractionMutex.withLock {
                if (isClosing) {
                    extractor.close()
                    if (pendingExtractor === extractor) {
                        pendingExtractor = null
                    }
                    return@withLock false
                }

                pdfExtractor = extractor
                pendingExtractor = null
                pageCount = pdfExtractor.getPageCount()
                true
            }
        }
    }

    private fun initReader() {
        val initialPageIndex = currentPageIndex
        adapter = TextReaderPageAdapter(::retryPage)
        layoutManager = LinearLayoutManager(this)
        binding.textPagesRecyclerView.adapter = adapter
        binding.textPagesRecyclerView.layoutManager = layoutManager
        binding.textPagesRecyclerView.itemAnimator = null
        initReaderTapListener()
        binding.textPagesRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateCurrentPageFromScroll()
            }
        })
        binding.textPagesRecyclerView.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    scheduleLoadVisiblePages()
                }

                override fun onChildViewDetachedFromWindow(view: View) = Unit
            },
        )

        adapter.submitPageCount(pageCount)
        adapter.applySettings(settings)
        applyReaderTheme()
        initControls()
        binding.textPagesRecyclerView.post {
            scrollToPage(initialPageIndex)
            binding.textPagesRecyclerView.post { loadVisiblePages() }
        }
    }

    private fun initControls() {
        binding.previousPageButton.setOnClickListener { scrollToPage(currentPageIndex - 1) }
        binding.nextPageButton.setOnClickListener { scrollToPage(currentPageIndex + 1) }
        binding.pageButton.setOnClickListener {
            showGoToPageDialog(this, binding.root, currentPageIndex, pageCount, ::scrollToPage)
        }
        binding.tocButton.setOnClickListener { showBookmarks() }
        binding.typographyButton.setOnClickListener { showTypographySheet() }
        binding.backToPdfButton.setOnClickListener { finish() }

        binding.pageSlider.valueFrom = if (pageCount > 1) 1f else 0f
        binding.pageSlider.valueTo = if (pageCount > 1) pageCount.toFloat() else 1f
        binding.pageSlider.stepSize = 1f
        binding.pageSlider.isEnabled = pageCount > 1
        binding.pageSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                scrollToPage(slider.value.toInt() - 1)
            }
        })
        updatePageControls()
        setReaderControlsTouchListeners()
        showReaderControlsTemporarily()
    }

    private fun initReaderTapListener() {
        val tapDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                    showReaderControlsTemporarilyOrHide()
                    return true
                }
            },
        )
        binding.textPagesRecyclerView.addOnItemTouchListener(
            object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(recyclerView: RecyclerView, event: MotionEvent): Boolean {
                    tapDetector.onTouchEvent(event)
                    return false
                }
            },
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setReaderControlsTouchListeners() {
        val listener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> keepReaderControlsVisible()
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> scheduleReaderControlsHide()
            }
            false
        }
        listOf(
            binding.readerControlsCard,
            binding.pageSlider,
            binding.previousPageButton,
            binding.pageButton,
            binding.nextPageButton,
            binding.tocButton,
            binding.typographyButton,
            binding.backToPdfButton,
        ).forEach { it.setOnTouchListener(listener) }
    }

    private fun showReaderControlsTemporarilyOrHide() {
        if (binding.readerControlsCard.visibility == View.VISIBLE) {
            hideReaderControls()
        } else {
            showReaderControlsTemporarily()
        }
    }

    private fun showReaderControlsTemporarily() {
        binding.readerControlsCard.visibility = View.VISIBLE
        scheduleReaderControlsHide()
    }

    private fun hideReaderControls() {
        controlsHideHandler.removeCallbacks(hideControlsRunnable)
        if (::binding.isInitialized) {
            binding.readerControlsCard.visibility = View.GONE
        }
    }

    private fun keepReaderControlsVisible() {
        controlsHideHandler.removeCallbacks(hideControlsRunnable)
    }

    private fun scheduleReaderControlsHide() {
        controlsHideHandler.removeCallbacks(hideControlsRunnable)
        controlsHideHandler.postDelayed(hideControlsRunnable, controlsHideDelayMillis)
    }

    private fun showTypographySheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = TextReaderTypographySheetBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        syncTypographySheet(sheetBinding)

        sheetBinding.fontSizeSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                updateSettings(settings.copy(fontSize = slider.value))
            }
        })
        sheetBinding.lineSpacingSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                updateSettings(settings.copy(lineSpacing = slider.value))
            }
        })
        sheetBinding.marginSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                updateSettings(settings.copy(horizontalMargin = slider.value.toInt()))
            }
        })
        configureThemeButton(sheetBinding.systemThemeButton, sheetBinding, ReaderTheme.SYSTEM)
        configureThemeButton(sheetBinding.lightThemeButton, sheetBinding, ReaderTheme.LIGHT)
        configureThemeButton(sheetBinding.sepiaThemeButton, sheetBinding, ReaderTheme.SEPIA)
        configureThemeButton(sheetBinding.darkThemeButton, sheetBinding, ReaderTheme.DARK)
        configureThemeButton(sheetBinding.blackThemeButton, sheetBinding, ReaderTheme.BLACK)
        configureThemeButton(sheetBinding.draculaThemeButton, sheetBinding, ReaderTheme.DRACULA)
        configureFontButton(sheetBinding.sansFontButton, sheetBinding, ReaderFontFamily.SANS)
        configureFontButton(sheetBinding.serifFontButton, sheetBinding, ReaderFontFamily.SERIF)
        configureFontButton(sheetBinding.monoFontButton, sheetBinding, ReaderFontFamily.MONO)
        sheetBinding.resetSettingsButton.setOnClickListener {
            updateSettings(TextReaderSettings())
            syncTypographySheet(sheetBinding)
        }
        dialog.show()
    }

    private fun syncTypographySheet(sheetBinding: TextReaderTypographySheetBinding) {
        sheetBinding.fontSizeSlider.value = settings.fontSize.coerceIn(
            sheetBinding.fontSizeSlider.valueFrom,
            sheetBinding.fontSizeSlider.valueTo,
        )
        sheetBinding.lineSpacingSlider.value = settings.lineSpacing.coerceIn(
            sheetBinding.lineSpacingSlider.valueFrom,
            sheetBinding.lineSpacingSlider.valueTo,
        )
        sheetBinding.marginSlider.value = settings.horizontalMargin.toFloat().coerceIn(
            sheetBinding.marginSlider.valueFrom,
            sheetBinding.marginSlider.valueTo,
        )

        val checkedThemeButtonId = themeButtonId(settings.theme)
        listOf(
            sheetBinding.systemThemeButton,
            sheetBinding.lightThemeButton,
            sheetBinding.sepiaThemeButton,
            sheetBinding.darkThemeButton,
            sheetBinding.blackThemeButton,
            sheetBinding.draculaThemeButton,
        ).forEach { button ->
            button.setCheckable(true)
            button.isChecked = button.id == checkedThemeButtonId
        }

        val checkedFontButtonId = fontButtonId(settings.fontFamily)
        listOf(
            sheetBinding.sansFontButton,
            sheetBinding.serifFontButton,
            sheetBinding.monoFontButton,
        ).forEach { button ->
            button.setCheckable(true)
            button.isChecked = button.id == checkedFontButtonId
        }
    }

    private fun configureThemeButton(
        button: MaterialButton,
        sheetBinding: TextReaderTypographySheetBinding,
        theme: ReaderTheme,
    ) {
        button.setOnClickListener {
            updateSettings(settings.copy(theme = theme))
            syncTypographySheet(sheetBinding)
        }
    }

    private fun configureFontButton(
        button: MaterialButton,
        sheetBinding: TextReaderTypographySheetBinding,
        fontFamily: ReaderFontFamily,
    ) {
        button.setOnClickListener {
            updateSettings(settings.copy(fontFamily = fontFamily))
            syncTypographySheet(sheetBinding)
        }
    }

    private fun updateSettings(newSettings: TextReaderSettings) {
        settings = newSettings
        settings.save(PreferenceManager.getDefaultSharedPreferences(this))
        applySettingsToPages()
        applyReaderTheme()
    }

    private fun applySettingsToPages() {
        if (binding.textPagesRecyclerView.isComputingLayout) {
            binding.textPagesRecyclerView.post { applySettingsToPages() }
            return
        }

        adapter.applySettings(settings)
        scheduleLoadVisiblePages()
    }

    private fun applyReaderTheme() {
        val colors = settings.theme.colors(binding.root)
        binding.textReaderRoot.setBackgroundColor(colors.background)
        binding.textPagesRecyclerView.setBackgroundColor(colors.background)
        binding.message.setTextColor(colors.label)
    }

    private fun themeButtonId(theme: ReaderTheme): Int {
        return when (theme) {
            ReaderTheme.SYSTEM -> R.id.systemThemeButton
            ReaderTheme.LIGHT -> R.id.lightThemeButton
            ReaderTheme.SEPIA -> R.id.sepiaThemeButton
            ReaderTheme.DARK -> R.id.darkThemeButton
            ReaderTheme.BLACK -> R.id.blackThemeButton
            ReaderTheme.DRACULA -> R.id.draculaThemeButton
        }
    }

    private fun fontButtonId(fontFamily: ReaderFontFamily): Int {
        return when (fontFamily) {
            ReaderFontFamily.SANS -> R.id.sansFontButton
            ReaderFontFamily.SERIF -> R.id.serifFontButton
            ReaderFontFamily.MONO -> R.id.monoFontButton
        }
    }

    private fun loadAround(pageIndex: Int) {
        for (index in pageIndex - PREFETCH_DISTANCE..pageIndex + PREFETCH_DISTANCE) {
            loadPage(index)
        }
    }

    private fun loadTargetWindow(pageIndex: Int) {
        for (index in (pageIndex - PREFETCH_DISTANCE)..(pageIndex + JUMP_LOAD_AHEAD)) {
            loadPage(index)
        }
    }

    private fun loadVisiblePages() {
        val firstVisiblePage = layoutManager.findFirstVisibleItemPosition()
        val lastVisiblePage = layoutManager.findLastVisibleItemPosition()
        if (firstVisiblePage == RecyclerView.NO_POSITION || lastVisiblePage == RecyclerView.NO_POSITION) {
            loadAround(currentPageIndex)
            return
        }

        for (index in (firstVisiblePage - PREFETCH_DISTANCE)..(lastVisiblePage + PREFETCH_DISTANCE)) {
            loadPage(index)
        }
    }

    private fun scheduleLoadVisiblePages() {
        if (!::binding.isInitialized) return

        binding.textPagesRecyclerView.removeCallbacks(loadVisiblePagesRunnable)
        binding.textPagesRecyclerView.post(loadVisiblePagesRunnable)
    }

    private fun retryPage(pageIndex: Int) {
        loadPage(pageIndex, force = true)
    }

    private fun loadPage(pageIndex: Int, force: Boolean = false) {
        if (pageIndex !in 0 until pageCount) return

        textCache[pageIndex]?.let { cachedText ->
            val currentState = adapter.pageState(pageIndex)
            if (currentState !is TextReaderPageState.Ready || currentState.text != cachedText) {
                updatePageState(TextReaderPageState.Ready(pageIndex, cachedText))
                scheduleLoadVisiblePages()
            }
            return
        }
        if (loadingPages.contains(pageIndex)) return
        if (!force) {
            when (adapter.pageState(pageIndex)) {
                is TextReaderPageState.Ready,
                is TextReaderPageState.Empty,
                is TextReaderPageState.Error -> return
                else -> Unit
            }
        }

        loadingPages.add(pageIndex)
        updatePageState(TextReaderPageState.Loading(pageIndex))
        val job = lifecycleScope.launch(Dispatchers.IO) {
            val state = try {
                val rawText = extractionMutex.withLock {
                    pdfExtractor.getPageTextOrThrow(pageIndex + 1)
                }
                val text = TextReaderTextFormatter.format(rawText)
                if (text.isBlank()) {
                    TextReaderPageState.Empty(pageIndex)
                } else {
                    TextReaderPageState.Ready(pageIndex, text)
                }
            } catch (throwable: Throwable) {
                TextReaderPageState.Error(pageIndex, throwable.message.orEmpty())
            }

            withContext(Dispatchers.Main) {
                loadingPages.remove(pageIndex)
                if (state is TextReaderPageState.Ready) {
                    cacheText(state.pageIndex, state.text)
                }
                updatePageState(state)
                scheduleLoadVisiblePages()
            }
        }
        extractionJobs.add(job)
        job.invokeOnCompletion { extractionJobs.remove(job) }
    }

    private fun updatePageState(state: TextReaderPageState) {
        if (binding.textPagesRecyclerView.isComputingLayout) {
            binding.textPagesRecyclerView.post { updatePageState(state) }
        } else {
            adapter.updatePageState(state)
        }
    }

    private fun cacheText(pageIndex: Int, text: String) {
        textCache[pageIndex] = text
        while (textCache.size > CACHE_PAGE_LIMIT) {
            val eldestPageIndex = textCache.entries.iterator().next().key
            textCache.remove(eldestPageIndex)
            if (eldestPageIndex != pageIndex) {
                updatePageState(TextReaderPageState.NotLoaded(eldestPageIndex))
            }
        }
    }

    private fun scrollToPage(pageIndex: Int) {
        if (pageCount <= 0) return

        currentPageIndex = pageIndex.coerceIn(0, pageCount - 1)
        layoutManager.scrollToPositionWithOffset(currentPageIndex, 0)
        loadTargetWindow(currentPageIndex)
        binding.textPagesRecyclerView.doOnNextLayout { loadVisiblePages() }
        updatePageControls()
        saveCurrentPage()
    }

    private fun updateCurrentPageFromScroll() {
        val firstVisiblePage = layoutManager.findFirstVisibleItemPosition()
        if (firstVisiblePage == RecyclerView.NO_POSITION) return

        if (firstVisiblePage == currentPageIndex) {
            loadVisiblePages()
            return
        }

        currentPageIndex = firstVisiblePage
        loadVisiblePages()
        updatePageControls()
        saveCurrentPage()
    }

    private fun saveCurrentPage() {
        val hash = fileHash ?: return
        if (savedPageIndex == currentPageIndex) return

        savedPageIndex = currentPageIndex
        lifecycleScope.launch {
            databaseManager.setPageNumber(hash, currentPageIndex)
        }
    }

    private fun updatePageControls() {
        if (pageCount <= 0) return

        binding.pageButton.text = getString(R.string.text_reader_page_counter, currentPageIndex + 1, pageCount)
        binding.pageSlider.value = (currentPageIndex + 1).toFloat().coerceIn(binding.pageSlider.valueFrom, binding.pageSlider.valueTo)
        binding.previousPageButton.isEnabled = currentPageIndex > 0
        binding.nextPageButton.isEnabled = currentPageIndex < pageCount - 1
    }

    private fun showBookmarks() {
        Intent(this, BookmarksActivity::class.java).also { bookmarkIntent ->
            bookmarkIntent.putExtra(PDF.filePathKey, pdfUri.toString())
            bookmarkIntent.putExtra(PDF.passwordKey, pdfPassword)
            bookmarkState.putInto(bookmarkIntent)
            startActivityForResult(bookmarkIntent, PDF.startBookmarksActivity)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PDF.startBookmarksActivity) {
            data?.let { bookmarkState = BookmarkState.from(it) }
            if (resultCode == PDF.BOOKMARK_RESULT_OK) {
                val pageIndex = data?.getIntExtra(PDF.chosenBookmarkKey, currentPageIndex) ?: return
                scrollToPage(pageIndex)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(CURRENT_PAGE_KEY, currentPageIndex)
        fileHash?.let { outState.putString(PDF.fileHashKey, it) }
        bookmarkState.putInto(outState)
        super.onSaveInstanceState(outState)
    }

    override fun finish() {
        if (!resultPrepared) {
            setPageResult(Activity.RESULT_OK)
        }
        super.finish()
    }

    override fun onDestroy() {
        isClosing = true
        controlsHideHandler.removeCallbacksAndMessages(null)
        if (::binding.isInitialized) {
            binding.textPagesRecyclerView.removeCallbacks(loadVisiblePagesRunnable)
        }
        setupJob?.cancel()
        synchronized(extractionJobs) {
            extractionJobs.toList()
        }.forEach { it.cancel() }
        CoroutineScope(Dispatchers.IO + NonCancellable).launch {
            extractionMutex.withLock {
                pendingExtractor?.close()
                pendingExtractor = null
                if (::pdfExtractor.isInitialized) {
                    pdfExtractor.close()
                }
            }
        }
        super.onDestroy()
    }

    private fun setPageResult(resultCode: Int) {
        resultPrepared = true
        setResult(
            resultCode,
            Intent().putExtra(PDF.pageNumberKey, currentPageIndex),
        )
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.message.visibility = View.GONE
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
    }

    private fun badFileExit() {
        if (::binding.isInitialized) {
            Snackbar.make(binding.root, getString(R.string.failed_to_extract_text), Snackbar.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, getString(R.string.failed_to_extract_text), Toast.LENGTH_SHORT).show()
        }
        setPageResult(Activity.RESULT_CANCELED)
        finish()
    }

    companion object {
        private const val CURRENT_PAGE_KEY = "CURRENT_TEXT_READER_PAGE"
        private const val PREFETCH_DISTANCE = 2
        private const val JUMP_LOAD_AHEAD = 8
        private const val CACHE_PAGE_LIMIT = 24
        private const val CONTROLS_EXTRA_HIDE_DELAY_MS = 1500L
    }
}
