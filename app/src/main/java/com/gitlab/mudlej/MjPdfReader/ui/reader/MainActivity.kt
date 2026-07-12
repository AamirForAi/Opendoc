// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.ui.confirmDialog
import com.gitlab.mudlej.MjPdfReader.data.*
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.databinding.PasswordDialogBinding
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.core.io.fileShareIntent
import com.gitlab.mudlej.MjPdfReader.core.io.imageShareIntent
import com.gitlab.mudlej.MjPdfReader.core.io.plainTextShareIntent
import com.gitlab.mudlej.MjPdfReader.core.ui.AppSnackbar
import com.gitlab.mudlej.MjPdfReader.core.ui.ColorUtil
import com.gitlab.mudlej.MjPdfReader.core.ui.showOptionalIcons
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.pdf.PdfPropertiesSummary
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.shockwave.pdfium.PdfPasswordException
import java.io.FileNotFoundException
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), ReaderUi {

    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    private lateinit var pref: Preferences
    private lateinit var reader: ReaderComposition

    private val vm: ReaderViewModel by viewModels()
    private val pdf: DocumentState get() = vm.doc

    private lateinit var actionBarMenu: Menu
    private lateinit var appTitle: TextView
    private lateinit var appTitlePageNumber: TextView

    private var doubleBackToExitPressedOnce = false
    private var savingProgressVisible = false
    private var taskDescriptionName: String? = null

    private val annotationController get() = reader.annotationController
    private val annotationSaveController get() = reader.annotationSaveController
    private val signatureController get() = reader.signatureController
    private val cropMarginsController get() = reader.cropMarginsController
    private val documentLoader get() = reader.documentLoader
    private val onlinePdfController get() = reader.onlinePdfController
    private val readerNavigationController get() = reader.readerNavigationController
    private val readerHistory get() = reader.readerHistory
    private val fullscreenController get() = reader.fullscreenController
    private val pdfRepository get() = reader.pdfRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pref = Preferences(PreferenceManager.getDefaultSharedPreferences(this))

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomActionBar()
        ColorUtil.colorize(this, window, supportActionBar)

        // To avoid FileUriExposedException, (https://stackoverflow.com/questions/38200282/)
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())

        reader = ReaderComposition(this, binding, vm, pref)
        documentLoader.applyTileRenderingPreferences()

        openInitialDocument(savedInstanceState)
        reader.wireViews()
        overrideOnBackButtonPressed()
    }

    private fun openInitialDocument(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState)
        }
        else {
            vm.incognito = intent.getBooleanExtra(PDF.incognitoKey, false)
            val intentUri = intent.data
            if (intentUri == null) {
                if (intent.getBooleanExtra(HomeActivity.EXTRA_OPEN_ONLINE_DIALOG, false)) {
                    onlinePdfController.showOpenOnlinePdfDialog()
                } else {
                    reader.pickFile()
                }
            } else {
                documentLoader.prepareNewDocument(intentUri)
            }
        }

        displayFromUri(pdf.uri, true)
    }

    fun isDisplayingUri(uri: String): Boolean {
        return pdf.uri?.toString() == uri
    }

    private fun setCustomActionBar() {
        val actionBar = supportActionBar
        // Disable the default and enable the custom
        actionBar?.setDisplayShowTitleEnabled(false)
        actionBar?.setDisplayShowCustomEnabled(true)
        actionBar?.elevation = 0F

        updateHomeUpIndicator()

        val customView: View = layoutInflater.inflate(R.layout.actionbar_title, null)
        appTitlePageNumber = customView.findViewById(R.id.actionbarPageNumber)
        appTitle = customView.findViewById(R.id.actionbarTitle)

        fun titleClickListener() {
            val title = pdf.getTitle()
            if (title.isNotBlank()) {
                AppSnackbar.make(binding.root, title, Snackbar.LENGTH_LONG).show()
            }
        }
        appTitle.setOnClickListener { titleClickListener() }
        appTitlePageNumber.setOnClickListener { titleClickListener() }

        // Apply the custom view
        actionBar?.customView = customView
    }

    private fun updateHomeUpIndicator() {
        val actionBar = supportActionBar
        val homeEnabled = !pref.getHomeDisabled()
        actionBar?.setDisplayHomeAsUpEnabled(homeEnabled)
        if (homeEnabled) {
            actionBar?.setHomeAsUpIndicator(R.drawable.ic_home)
        }
    }

    override fun runAfterDirtyAnnotationPrompt(discardAction: () -> Unit) {
        if (!annotationController.hasUnsavedAnnotations) {
            discardAction()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.unsaved_highlights)
            .setMessage(R.string.unsaved_highlights_prompt)
            .setPositiveButton(R.string.save_highlights) { _, _ ->
                annotationSaveController.saveHighlights(postSaveAction = discardAction)
            }
            .setNegativeButton(R.string.discard) { _, _ ->
                clearUnsavedAnnotationState()
                discardAction()
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun clearUnsavedAnnotationState() {
        signatureController.cancelPlacement()
        annotationSaveController.clearPendingRequests()
        annotationController.clearJournal()
        updateDirtyUi()
    }

    internal fun confirmDiscardAnnotations() {
        confirmDialog(
            this,
            R.string.discard_unsaved_highlights_title,
            getString(R.string.discard_unsaved_highlights_message),
            R.string.discard,
        ) {
            clearUnsavedAnnotationState()
            reloadPdf()
        }
    }

    fun displayFromUri(uri: Uri?, savePassword: Boolean = false) {
        documentLoader.displayFromUri(uri, savePassword)
    }

    override fun updateTitle() {
        if (pdf.name.isNotBlank() && taskDescriptionName != pdf.name) {
            taskDescriptionName = pdf.name
            setTaskDescription(ActivityManager.TaskDescription(pdf.name))
        }
        appTitle.text = pdf.getTitle()
        appTitlePageNumber.text = pdf.getPageCounterText()
        appTitlePageNumber.visibility = if (pref.getShowAppBarPageCount() && pdf.hasFile() && pdf.length > 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
        reader.fullScreenOptionsManager.refreshInfo()
    }

    override fun updateActionBar() {
        if (!::actionBarMenu.isInitialized) {
            return
        }

        reader.toolbarActionController.update(actionBarMenu)
    }

    internal fun checkAlwaysHorizontal() {
        if (pref.getAlwaysHorizontal() && vm.isPortrait) {
            rotateScreen()
        }
        if (!pref.getAlwaysHorizontal() && !vm.isPortrait) {
            rotateScreen()
        }
    }

    internal fun maybeRestoreAnnotations(documentUri: Uri?, loadToken: Long) {
        val uri = documentUri ?: return
        lifecycleScope.launch {
            val hasJournal = withContext(Dispatchers.IO) { annotationController.hasJournal(uri) }
            if (!hasJournal || !vm.isCurrent(loadToken, uri)) {
                return@launch
            }
            if (annotationController.isSessionOwned(uri)) {
                replayAnnotations(uri, loadToken)
            } else {
                promptRestoreAnnotations(uri, loadToken)
            }
        }
    }

    private fun promptRestoreAnnotations(documentUri: Uri, loadToken: Long) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restore_unsaved_highlights_title)
            .setMessage(R.string.restore_unsaved_highlights_message)
            .setCancelable(false)
            .setPositiveButton(R.string.restore) { _, _ ->
                lifecycleScope.launch { replayAnnotations(documentUri, loadToken) }
            }
            .setNegativeButton(R.string.discard) { _, _ ->
                annotationController.clearJournal(documentUri)
                updateDirtyUi()
            }
            .show()
    }

    private suspend fun replayAnnotations(documentUri: Uri, loadToken: Long) {
        if (!vm.isCurrent(loadToken, documentUri)) {
            return
        }
        annotationController.replayJournal()
        updateDirtyUi()
    }

    override fun updateDirtyUi() {
        val visible = annotationController.hasUnsavedAnnotations
        val saving = annotationController.isSaving
        binding.saveAnnotationsFab.visibility = if (visible) View.VISIBLE else View.GONE
        binding.discardAnnotationsFab.visibility = if (visible) View.VISIBLE else View.GONE
        binding.saveAnnotationsFab.isEnabled = visible && !saving
        binding.discardAnnotationsFab.isEnabled = visible && !saving
        binding.saveAnnotationsFab.alpha = if (saving) 0.5f else 1f
        binding.discardAnnotationsFab.alpha = if (saving) 0.5f else 1f
        if (saving && !savingProgressVisible) {
            savingProgressVisible = true
            binding.progressBar.isIndeterminate = true
            binding.progressBar.visibility = View.VISIBLE
        } else if (!saving && savingProgressVisible) {
            savingProgressVisible = false
            hideProgress()
        }
        updateDirtyUiPosition()
    }

    override fun updateDirtyUiPosition() {
        val params = binding.saveAnnotationsFab.layoutParams as ConstraintLayout.LayoutParams
        val defaultBottomMargin = (24 * resources.displayMetrics.density).toInt()
        val cardAtBottom = reader.inlineAnnotationActionController.isCardAtBottom()
        val bottomMargin = if (cardAtBottom && binding.textSelectionActionCard.height > 0) {
            binding.textSelectionActionCard.height + (32 * resources.displayMetrics.density).toInt()
        } else {
            defaultBottomMargin
        }
        if (params.bottomMargin != bottomMargin) {
            params.bottomMargin = bottomMargin
            binding.saveAnnotationsFab.layoutParams = params
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    internal fun rotateScreen() {
        requestedOrientation = if (vm.isPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        vm.togglePortrait()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        fullscreenController.refreshOnWindowFocus(hasFocus)
    }

    public override fun onResume() {
        super.onResume()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (pref.getScreenOn()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (::actionBarMenu.isInitialized) {
            updateActionBar()
        }
        updateHomeUpIndicator()
        reader.onResume()

        // check if there is a pdf at first

        if (pdf.uri != null) {
            binding.pickFileButton.visibility = View.GONE
        }
        else {
            binding.pickFileButton.visibility = View.VISIBLE
        }

        // restore the full screen mode if was toggled On
        fullscreenController.restoreFullScreenIfNeeded()
    }

    internal fun shareFile(uri: Uri?, asImage: Boolean = false) {
        if (uri == null) {
            checkHasFile()  // only to show the message
            return
        }
        val sharingIntent: Intent =
            if (uri.scheme != null && uri.scheme!!.startsWith("http")) {
                plainTextShareIntent(getString(R.string.share_file), pdf.uri.toString())
            }
            else if (asImage) {
                imageShareIntent(getString(R.string.share_file), pdf.name, uri)
            }
            else {
                fileShareIntent(getString(R.string.share_file), pdf.name, uri)
            }

        try {
            startActivity(sharingIntent)
        }
        catch (e: Throwable) {
            AppSnackbar.make(binding.root, "Error sharing the file. (${e.message})", Snackbar.LENGTH_LONG).show()
        }
    }

    internal fun handleFileOpeningError(exception: Throwable) {
        val fileHash = pdf.fileHash
        if (exception is PdfPasswordException && fileHash != null) {
            if (pdf.password != null) {
                AppSnackbar.make(binding.root, R.string.wrong_password, Snackbar.LENGTH_SHORT).show()
                pdf.password = null         // prevent the toast if the user rotates the screen
            }

            lifecycleScope.launch {
                pdf.password = pdfRepository.findPdfPassword(fileHash)
                withContext(Dispatchers.Main) {
                    if (pdf.password != null) {
                        displayFromUri(pdf.uri)
                    }
                    else {
                        askForPdfPassword()
                    }
                }
            }
        }
        else if (couldNotOpenFileDueToMissingPermission(exception)) {
            reader.readFileErrorPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        else if (shouldReturnToHomeForRelocate(exception)) {
            returnToHomeForRelocate()
        }
        else {
            AppSnackbar.make(binding.root, R.string.file_opening_error, Snackbar.LENGTH_LONG).show()
            Log.e(TAG, getString(R.string.file_opening_error), exception)
        }
    }

    private fun shouldReturnToHomeForRelocate(exception: Throwable): Boolean {
        if (!intent.getBooleanExtra(HomeActivity.EXTRA_FROM_HOME, false)) {
            return false
        }
        if (intent.getStringExtra(HomeActivity.EXTRA_RECORD_HASH) == null) {
            return false
        }
        var cause: Throwable? = exception
        while (cause != null) {
            if (cause is SecurityException || cause is FileNotFoundException) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun returnToHomeForRelocate() {
        Intent(this, HomeActivity::class.java).also { homeIntent ->
            homeIntent.putExtra(
                HomeActivity.EXTRA_RELOCATE_HASH,
                intent.getStringExtra(HomeActivity.EXTRA_RECORD_HASH)
            )
            startActivity(homeIntent)
        }
        finish()
    }

    private fun couldNotOpenFileDueToMissingPermission(e: Throwable): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) return false
        val exceptionMessage = e.message
        return e is FileNotFoundException && exceptionMessage != null
            && exceptionMessage.contains(getString(R.string.permission_denied))
    }

    internal fun restartAppIfGranted(isPermissionGranted: Boolean) {
        if (isPermissionGranted) {
            // This is a quick and dirty way to make the system restart the current activity *and the current app process*.
            // This is needed because on Android 6 storage permission grants do not take effect until
            // the app process is restarted.
            exitProcess(0)
        }
        else {
            AppSnackbar.make(binding.root, R.string.file_opening_error, Snackbar.LENGTH_LONG).show()
        }
    }

    internal fun reloadPdf() {
        if (checkHasFile()) {
            recreate()
        }
    }

    internal fun toggleCropMargins() {
        if (!checkHasFile()) {
            return
        }
        val enableCropMargins = !vm.cropMarginsEnabled
        reader.setCropMarginsEnabled(enableCropMargins)
        if (enableCropMargins) {
            cropMarginsController.startOrApply(
                pdf.fileHash,
                vm.currentLoadToken,
                pdf.uri,
                binding.pdfView.pageCount,
            )
        } else {
            cropMarginsController.cancel()
            recreate()
        }
    }

    internal fun downloadOrShowDownloadedFile(uri: Uri) {
        onlinePdfController.downloadOrShowDownloadedFile(uri, lastCustomNonConfigurationInstance)
    }

    @Deprecated("Use a {@link androidx.lifecycle.ViewModel} to store non config state.")
    override fun onRetainCustomNonConfigurationInstance(): Any {
        return onlinePdfController.retainSnapshot()
    }

    override fun onStop() {
        if (::reader.isInitialized) {
            reader.autoScrollSpeedStore.flushPendingSave()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (::reader.isInitialized) {
            reader.autoScrollSpeedStore.flushPendingSave()
            cropMarginsController.cancel()
            reader.inlineAnnotationActionController.hideActions()
        }
        super.onDestroy()
    }

    override fun hideProgress() {
        binding.progressBar.visibility = View.GONE
        binding.progressBar.isIndeterminate = true
        binding.progressBar.progress = 0
    }

    private fun askForPdfPassword() {
        val dialogBinding = PasswordDialogBinding.inflate(layoutInflater)
        showAskForPasswordDialog(this, pdf, dialogBinding, ::displayFromUri)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        this.actionBarMenu = menu
        menu.showOptionalIcons(this)
        updateActionBar()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> navigateHome()
            R.id.toolbarPrimaryActionOption,
            R.id.toolbarSecondaryActionOption -> if (!reader.toolbarActionController.handle(item)) return super.onOptionsItemSelected(item)
            R.id.readerActionsOption -> reader.readerMenu.show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun navigateHome() {
        runAfterDirtyAnnotationPrompt {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (reader.volumeKeyPager.handleKeyDown(keyCode)) {
            return true
        }
        if (reader.mousePager.handleKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (reader.mousePager.handleGenericMotionEvent(ev)) {
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    internal fun showFileMetadata() {
        if (!checkHasFile()) {
            return
        }

        val uri = pdf.uri
        lifecycleScope.launch {
            val fileSizeBytes = withContext(Dispatchers.IO) { queryFileSizeBytes(uri) }
            val pageSize = withContext(Dispatchers.Default) {
                PdfPropertiesSummary.formatPageSizes(binding.pdfView, getString(R.string.pdf_page_size_mixed))
            }
            val fonts = withContext(Dispatchers.Default) {
                PdfPropertiesSummary.formatFonts(
                    binding.pdfView,
                    getString(R.string.font_embedded),
                    getString(R.string.font_not_embedded),
                )
            }
            showMetaDialog(this@MainActivity, binding.pdfView.documentMeta, pdf.name, fileSizeBytes, pageSize, fonts)
        }
    }

    private fun queryFileSizeBytes(uri: Uri?): Long? {
        if (uri == null) {
            return null
        }
        val heldBytes = PdfBytesHolder.bytesFor(uri.toString())
        if (heldBytes != null) {
            return heldBytes.size.toLong()
        }
        return runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                fd.statSize.takeIf { it >= 0 }
            }
        }.getOrNull()
    }

    override fun checkHasFile(): Boolean {
        if (!pdf.hasFile()) {
            AppSnackbar.make(
                binding.root, getString(R.string.no_pdf_in_app),
                Snackbar.LENGTH_LONG
            ).show()
            return false
        }
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        vm.captureViewStateForSave(binding.pdfView.captureViewState())
        signatureController.capturePlacementForState()
        readerNavigationController.saveState(outState)
        readerHistory.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun restoreInstanceState(savedState: Bundle) {
        readerNavigationController.restoreState(savedState)
        readerHistory.restoreState(savedState)
        updateDirtyUi()
    }

    private fun overrideOnBackButtonPressed() {
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (annotationController.hasUnsavedAnnotations) {
                    runAfterDirtyAnnotationPrompt { leaveReader(this) }
                    return
                }
                if (!pref.getDoubleTapToExitEnabled()
                    || intent.getBooleanExtra(HomeActivity.EXTRA_FROM_HOME, false)
                    || doubleBackToExitPressedOnce
                ) {
                    leaveReader(this)
                } else {
                    AppSnackbar.make(binding.root, getString(R.string.press_back_again), Snackbar.LENGTH_LONG).show()
                    doubleBackToExitPressedOnce = true

                    lifecycleScope.launch {
                        delay(2500)
                        doubleBackToExitPressedOnce = false
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun leaveReader(callback: OnBackPressedCallback) {
        val launchedByPickerFlow = pref.getHomeDisabled()
                && intent.action == null
                && intent.data == null
        if (launchedByPickerFlow && pdf.uri != null) {
            doubleBackToExitPressedOnce = false
            reader.pickFileOnBackPressed()
        } else if (intent.getBooleanExtra(HomeActivity.EXTRA_FROM_HOME, false)) {
            navigateHome()
        } else {
            callback.isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }

}
