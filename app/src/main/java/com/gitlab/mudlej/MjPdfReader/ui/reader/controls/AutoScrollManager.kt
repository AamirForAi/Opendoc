// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.controls

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.resolveReadingLayout
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign

class AutoScrollManager(
    private val binding: ActivityMainBinding,
    private val vm: ReaderViewModel,
    private val preferences: Preferences,
    private val onSpeedChanged: (Int) -> Unit,
) {

    private companion object {
        const val SPEED_UPDATE_DELAY = 100L
        const val PAGES_LOAD_INTERVAL_MS = 300L
        const val MAX_FRAME_DELTA_SECONDS = 0.1
        const val SPEED_FACTOR = 50
    }

    private val speedUpdateHandler = Handler(Looper.getMainLooper())
    private var speedUpdateRunnable: Runnable? = null
    private var scrollBy = 0.0
    private var interactionPointerCount = 0
    private var pausedByInteraction = false
    private var lastFrameTimeNanos = 0L
    private var lastPagesLoadMs = 0L
    private var frameScheduled = false

    private val frameRunnable = object : Runnable {
        override fun run() {
            frameScheduled = false
            onFrame()
        }
    }

    fun setup() {
        setSpeed(vm.doc.autoScrollSpeed ?: preferences.getScrollSpeed())

        binding.autoScrollButton.setOnClickListener { toggleControls() }
        binding.incScrollSpeedButton.setOnClickListener { increaseSpeed() }
        binding.decScrollSpeedButton.setOnClickListener { decreaseSpeed() }
        binding.incScrollSpeedButton.setOnLongClickListener { startRepeatingSpeedChange(isIncreasing = true) }
        binding.decScrollSpeedButton.setOnLongClickListener { startRepeatingSpeedChange(isIncreasing = false) }
        binding.reverseScrollDirectionButton.setOnClickListener { scrollBy = -scrollBy }
        binding.toggleAutoScrollButton.setOnClickListener { toggleAutoScroll() }
    }

    fun setSpeed(speed: Int) {
        setScrollBy(-Preferences.AUTO_SCROLL_UNIT * speed.coerceAtLeast(1), notify = false)
    }

    fun stop() {
        binding.toggleAutoScrollButton.setIconResource(R.drawable.ic_play_arrow)
        cancelFrame()
        interactionPointerCount = 0
        pausedByInteraction = false
        val wasScrolling = vm.isAutoScrolling
        vm.isAutoScrolling = false
        if (wasScrolling) {
            binding.pdfView.loadPages()
        }
    }

    fun hideControls() {
        binding.autoScrollLayout.visibility = View.GONE
        binding.autoScrollSpeedText.visibility = View.GONE
        binding.autoScrollButton.isChecked = false
        vm.isAutoScrollClicked = false
    }

    fun handleUserInteraction(motionEvent: MotionEvent) {
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> pauseForInteraction(motionEvent)

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> resumeAfterInteraction(motionEvent)
        }
    }

    private fun toggleControls() {
        binding.autoScrollButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        if (binding.autoScrollLayout.visibility == View.VISIBLE) {
            hideControls()
        }
        else {
            showControls()
        }
    }

    private fun showControls() {
        binding.autoScrollLayout.visibility = View.VISIBLE
        binding.autoScrollSpeedText.visibility = View.VISIBLE
        binding.autoScrollButton.isChecked = true
        vm.isAutoScrollClicked = true
    }

    private fun increaseSpeed() {
        setScrollBy(changeScrollingSpeed(scrollBy, Preferences.AUTO_SCROLL_UNIT, isIncreasing = true))
    }

    private fun decreaseSpeed() {
        if (scrollBy.absoluteValue > Preferences.AUTO_SCROLL_UNIT) {
            setScrollBy(changeScrollingSpeed(scrollBy, Preferences.AUTO_SCROLL_UNIT, isIncreasing = false))
        }
    }

    private fun startRepeatingSpeedChange(isIncreasing: Boolean): Boolean {
        speedUpdateRunnable = Runnable {
            if (!binding.incScrollSpeedButton.isPressed && !binding.decScrollSpeedButton.isPressed) {
                return@Runnable
            }

            setScrollBy(changeScrollingSpeed(scrollBy, Preferences.AUTO_SCROLL_UNIT, isIncreasing))
            speedUpdateRunnable?.let { speedUpdateHandler.postDelayed(it, SPEED_UPDATE_DELAY) }
        }
        speedUpdateRunnable?.let { speedUpdateHandler.postDelayed(it, SPEED_UPDATE_DELAY) }
        return true
    }

    private fun toggleAutoScroll() {
        vm.isAutoScrolling = !vm.isAutoScrolling

        if (!vm.isAutoScrolling) {
            stop()
            return
        }

        binding.toggleAutoScrollButton.setIconResource(R.drawable.ic_pause)
        start()
    }

    private fun start() {
        cancelFrame()
        lastFrameTimeNanos = 0L
        lastPagesLoadMs = 0L
        scheduleFrame()
    }

    private fun scheduleFrame() {
        if (frameScheduled) {
            return
        }
        frameScheduled = true
        binding.pdfView.postOnAnimation(frameRunnable)
    }

    private fun cancelFrame() {
        binding.pdfView.removeCallbacks(frameRunnable)
        frameScheduled = false
        lastFrameTimeNanos = 0L
    }

    private fun onFrame() {
        if (!vm.isAutoScrolling || pausedByInteraction) {
            return
        }
        if (!shouldContinueAutoScrolling(scrollBy)) {
            stop()
            return
        }

        val frameTimeNanos = System.nanoTime()
        if (lastFrameTimeNanos != 0L) {
            val deltaSeconds = ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000.0)
                .coerceAtMost(MAX_FRAME_DELTA_SECONDS)
            val distance = (scrollBy * SPEED_FACTOR * deltaSeconds).toFloat()

            if (resolveReadingLayout(preferences).swipeHorizontal) {
                binding.pdfView.moveRelativeTo(distance, 0F)
            }
            else {
                binding.pdfView.moveRelativeTo(0F, distance)
            }
            loadPagesThrottled()
        }
        lastFrameTimeNanos = frameTimeNanos

        if (vm.isAutoScrolling && shouldContinueAutoScrolling(scrollBy)) {
            scheduleFrame()
        }
        else if (vm.isAutoScrolling) {
            stop()
        }
    }

    private fun loadPagesThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastPagesLoadMs < PAGES_LOAD_INTERVAL_MS) {
            return
        }
        lastPagesLoadMs = now
        binding.pdfView.loadPages()
    }

    private fun pauseForInteraction(motionEvent: MotionEvent) {
        interactionPointerCount = motionEvent.pointerCount
        if (!vm.isAutoScrolling) {
            return
        }

        cancelFrame()
        pausedByInteraction = true
    }

    private fun resumeAfterInteraction(motionEvent: MotionEvent) {
        interactionPointerCount = when (motionEvent.actionMasked) {
            MotionEvent.ACTION_POINTER_UP -> (motionEvent.pointerCount - 1).coerceAtLeast(0)
            else -> 0
        }
        if (interactionPointerCount > 0 || !pausedByInteraction || !vm.isAutoScrolling) {
            return
        }

        pausedByInteraction = false
        start()
    }

    private fun shouldContinueAutoScrolling(scrollBy: Double): Boolean {
        return if (scrollBy < 0) {
            binding.pdfView.positionOffset < 1F
        }
        else {
            binding.pdfView.positionOffset > 0F
        }
    }

    private fun setScrollBy(newScrollBy: Double, notify: Boolean = true) {
        val direction = if (newScrollBy > 0) 1.0 else -1.0
        scrollBy = direction * newScrollBy.absoluteValue.coerceAtLeast(Preferences.AUTO_SCROLL_UNIT)
        val speed = simplifySpeed(scrollBy)
        binding.autoScrollSpeedText.text = speed.toString()
        if (notify) {
            vm.doc.autoScrollSpeed = speed
            onSpeedChanged(speed)
        }
    }

    private fun simplifySpeed(scrollBy: Double): Int {
        return (scrollBy.absoluteValue * (1 / Preferences.AUTO_SCROLL_UNIT)).roundToInt().coerceAtLeast(1)
    }

    private fun changeScrollingSpeed(scrollBy: Double, interval: Double, isIncreasing: Boolean): Double {
        val newSpeed = if (isIncreasing) {
            (scrollBy.absoluteValue + interval) * scrollBy.sign
        }
        else if (scrollBy.absoluteValue > interval) {
            (scrollBy.absoluteValue - interval) * scrollBy.sign
        }
        else {
            scrollBy
        }

        return newSpeed
    }
}
