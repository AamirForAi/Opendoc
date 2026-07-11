package com.gitlab.mudlej.MjPdfReader.ui.main.controls

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.ui.main.ReaderViewModel
import com.gitlab.mudlej.MjPdfReader.util.divideToPercent
import com.google.android.material.button.MaterialButton
import java.util.Date
import kotlin.reflect.KFunction1


class FullScreenOptionsManager(
    private val binding: ActivityMainBinding,
    private val vm: ReaderViewModel,
    private val delay: Long,
    private val preferences: Preferences,
) {

    enum class VisibilityState { VISIBLE, INVISIBLE }

    private val delayHandler = Handler(Looper.getMainLooper())
    
    private var visibility: VisibilityState = VisibilityState.INVISIBLE
    private var labelVisibility: VisibilityState = VisibilityState.VISIBLE

    private val viewsList: MutableList<View> = mutableListOf(
        binding.fullScreenButtonsLayout,
        binding.fullScreenInfoLayout,
        binding.exitFullScreenButton,
        binding.rotateScreenButton,

        binding.brightnessLayout,
        binding.brightnessButton,
        binding.brightnessSeekBar,
        binding.brightnessPercentage,

        binding.autoScrollLayout,
        binding.autoScrollButton,
        binding.decScrollSpeedButton,
        binding.toggleAutoScrollButton,
        binding.reverseScrollDirectionButton,
        binding.incScrollSpeedButton,

        binding.toggleHorizontalSwipeButton,
        binding.toggleZoomLockButton,
        binding.screenshotButton,
        binding.toggleLabelButton,
    )
    private val registeredButtonLabels = linkedMapOf<MaterialButton, String?>()

    init {
        setOnTouchListenerForAll()
    }

    fun isVisible() = visibility == VisibilityState.VISIBLE

    fun showAll() {
        if (vm.isFullScreenToggled) {
            showFullScreenButtons()
        }
        showPageHandle()
        showAutoScrollLayout()
        showBrightnessLayout()
        visibility = VisibilityState.VISIBLE
    }

    fun hideAll() {
        hideFullScreenButtons()
        hidePageHandle()
        hideAutoScrollLayout()
        hideBrightnessLayout()
        visibility = VisibilityState.INVISIBLE
    }

    fun toggleAll() {
        if (isVisible()) hideAll() else showAll()
    }

    fun showAllDelayed() {
        delayAction(::showAll)
    }

    fun hideAllDelayed() {
        delayAction(::hideAll)
    }

    fun toggleAllDelayed() {
        delayAction(::toggleAll)
    }

    fun showAllTemporarily() {
        doTemporarily(::showAll, ::hideAll)
    }

    fun hideAllTemporarily() {
        doTemporarily(::hideAll, ::showAll)
    }

    fun toggleAllTemporarily() {
        doTemporarily(::toggleAll, ::toggleAll)
    }

    fun showAllTemporarilyOrHide() {
        if (!isVisible()) {
            showAllTemporarily()
        }
        else {
            hideAll()
        }
    }

    fun permanentlyHidePageHandle() {
        binding.pdfView.scrollHandle?.permanentHide()
    }

    fun refreshInfo() {
        val shouldShowInfo = updateInfoContent()
            && binding.fullScreenButtonsLayout.visibility == View.VISIBLE

        binding.fullScreenInfoLayout.visibility = if (shouldShowInfo) View.VISIBLE else View.GONE
        binding.pdfView.scrollHandle?.setReadingProgressTextEnabled(!shouldShowInfo)
    }

    fun registerFullScreenButton(button: MaterialButton, label: String?) {
        if (!viewsList.contains(button)) {
            viewsList.add(button)
        }
        registeredButtonLabels[button] = label
        button.setOnTouchListener(getOnTouchListener())
        if (labelVisibility == VisibilityState.INVISIBLE) {
            button.text = ""
            makeButtonCircular(button.context, button)
        }
    }

    private fun updateInfoContent(): Boolean {
        val context = binding.root.context
        val settings = FullScreenInfoSettings.from(preferences)
        val titleVisible = settings.showPdfName && vm.doc.name.isNotBlank()
        val pageInfo = getPageInfo(context, settings)

        binding.fullScreenInfoTime.text = DateFormat.getTimeFormat(context).format(Date())
        binding.fullScreenInfoTime.visibility = if (settings.showTime) View.VISIBLE else View.GONE
        binding.fullScreenInfoTitle.text = vm.doc.getTitle()
        binding.fullScreenInfoTitle.visibility = if (titleVisible) View.VISIBLE else View.GONE
        binding.fullScreenInfoPage.text = pageInfo.orEmpty()
        binding.fullScreenInfoPage.visibility = if (pageInfo != null) View.VISIBLE else View.GONE

        return settings.showTime || titleVisible || pageInfo != null
    }

    private fun getPageInfo(context: Context, settings: FullScreenInfoSettings): String? {
        val pageNumber = vm.doc.pageNumber + 1
        val pageCount = vm.doc.length.coerceAtLeast(pageNumber)
        val percentage = pageNumber.divideToPercent(pageCount)

        return when {
            settings.showPageNumber && settings.showReadingPercentage -> context.getString(
                R.string.fullscreen_page_info,
                pageNumber,
                pageCount,
                percentage,
            )
            settings.showPageNumber -> context.getString(
                R.string.fullscreen_page_number_info,
                pageNumber,
                pageCount,
            )
            settings.showReadingPercentage -> context.getString(
                R.string.fullscreen_percentage_info,
                percentage,
            )
            else -> null
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    fun getOnTouchListener(): View.OnTouchListener {
        val isEventFullyConsumed = false    // false so clickOnListener will be triggered
        return View.OnTouchListener { _, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> delayHandler.reset()
                MotionEvent.ACTION_UP -> hideAllDelayed()
            }
            isEventFullyConsumed
        }
    }

    fun isLabelsVisible(): Boolean {
        return labelVisibility == VisibilityState.VISIBLE
    }

    fun toggleLabelVisibility(context: Context, drawableOf: KFunction1<Int, Drawable?>, getLabel: KFunction1<Int, String?>) {
        binding.apply {
            val buttons = linkedMapOf(
                exitFullScreenButton to getLabel(R.string.exit),
                rotateScreenButton to getLabel(R.string.rotate),
                brightnessButton to getLabel(R.string.brightness),
                autoScrollButton to getLabel(R.string.auto_scroll),
                toggleHorizontalSwipeButton to getLabel(R.string.horizontal_lock),
                toggleZoomLockButton to getLabel(R.string.zoom_lock),
                screenshotButton to getLabel(R.string.screenshot),
                toggleLabelButton to getLabel(R.string.hide_labels)
            )
            buttons.putAll(registeredButtonLabels)
            if (labelVisibility == VisibilityState.VISIBLE) {
                buttons.keys.forEach { button ->
                    button.text = ""
                    makeButtonCircular(context, button)
                }
                toggleLabelButton.icon = drawableOf(R.drawable.ic_double_arrow_right)
            }
            else {
                buttons.forEach { (button, text) ->
                    button.text = text.orEmpty()
                    resetButtonShape(button)
                }
                toggleLabelButton.icon = drawableOf(R.drawable.ic_double_arrow_left)
            }
        }
        labelVisibility = inverseVisibility(labelVisibility)
    }

    private fun makeButtonCircular(context: Context, button: MaterialButton) {
        val scale = context.resources.displayMetrics.density
        val iconSizeDp = 24
        val iconSizePx = (iconSizeDp * scale).toInt()

        val circleFactor = 1.9
        val buttonWidthPx = iconSizePx * circleFactor

        val params = button.layoutParams
        params.width = buttonWidthPx.toInt()
        button.layoutParams = params
    }

    private fun resetButtonShape(button: MaterialButton) {
        button.layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
    }

    // -------------
    private fun delayAction(action: Runnable) {
        delayHandler.reset()
        delayHandler.postDelayed(action, delay)
    }

    private fun doTemporarily(action: Runnable, undoAction: Runnable) {
        delayHandler.reset()
        action.run()
        delayHandler.postDelayed(undoAction, delay)
    }

    private fun showFullScreenButtons() = changeFullScreenButtonsVisibility(true)

    private fun hideFullScreenButtons() = changeFullScreenButtonsVisibility(false)

    private fun changeFullScreenButtonsVisibility(isVisible: Boolean) {
        val visibility = if (isVisible) View.VISIBLE else View.GONE
        binding.fullScreenButtonsLayout.visibility = visibility
        if (isVisible) {
            refreshInfo()
        }
        else {
            binding.fullScreenInfoLayout.visibility = View.GONE
            binding.pdfView.scrollHandle?.setReadingProgressTextEnabled(true)
        }
    }

    private fun showPageHandle() {
        binding.pdfView.scrollHandle?.customShow()
    }

    private fun hidePageHandle() {
        binding.pdfView.scrollHandle?.customHide()
    }

    private fun showAutoScrollLayout() {
        if (vm.isFullScreenToggled && vm.isAutoScrollClicked) {
            binding.autoScrollLayout.visibility = View.VISIBLE
            binding.autoScrollSpeedText.visibility = View.VISIBLE
        }
    }

    private fun hideAutoScrollLayout() {
        if (vm.isFullScreenToggled && vm.isAutoScrollClicked) {
            binding.autoScrollLayout.visibility = View.GONE
            binding.autoScrollSpeedText.visibility = View.GONE
        }
    }

    private fun showBrightnessLayout() {
        if (vm.isFullScreenToggled && vm.isBrightnessClicked) {
            binding.brightnessLayout.visibility = View.VISIBLE
        }
    }

    private fun hideBrightnessLayout() {
        if (vm.isFullScreenToggled && vm.isBrightnessClicked) {
            binding.brightnessLayout.visibility = View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setOnTouchListenerForAll() {
        viewsList.forEach { it.setOnTouchListener(getOnTouchListener()) }
    }

    private fun Handler.reset() {
        this.removeCallbacksAndMessages(null)
    }

    private fun inverseVisibility(visibility: VisibilityState): VisibilityState {
        return if (visibility == VisibilityState.VISIBLE) VisibilityState.INVISIBLE
        else VisibilityState.VISIBLE
    }

    private data class FullScreenInfoSettings(
        val showTime: Boolean,
        val showPdfName: Boolean,
        val showPageNumber: Boolean,
        val showReadingPercentage: Boolean,
    ) {
        companion object {
            fun from(preferences: Preferences): FullScreenInfoSettings {
                return FullScreenInfoSettings(
                    showTime = preferences.getFullScreenInfoShowTime(),
                    showPdfName = preferences.getFullScreenInfoShowPdfName(),
                    showPageNumber = preferences.getFullScreenInfoShowPageNumber(),
                    showReadingPercentage = preferences.getFullScreenInfoShowReadingPercentage(),
                )
            }
        }
    }

}
