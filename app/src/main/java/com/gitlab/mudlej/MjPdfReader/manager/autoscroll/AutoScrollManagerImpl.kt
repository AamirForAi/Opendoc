package com.gitlab.mudlej.MjPdfReader.manager.autoscroll

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.PDF
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import kotlin.math.absoluteValue
import kotlin.math.sign

class AutoScrollManagerImpl(
    private val binding: ActivityMainBinding,
    private val pdf: PDF,
    private val preferences: Preferences,
    private val onSpeedChanged: (Int) -> Unit,
) : AutoScrollManager {

    private companion object {
        const val AUTO_SCROLL_DELAY = 1L
        const val SPEED_UPDATE_DELAY = 100L
    }

    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private val speedUpdateHandler = Handler(Looper.getMainLooper())
    private var speedUpdateRunnable: Runnable? = null
    private var scrollBy = 0.0
    private var interactionPointerCount = 0
    private var pausedByInteraction = false

    override fun setup() {
        setSpeed(pdf.autoScrollSpeed ?: preferences.getScrollSpeed())

        binding.autoScrollButton.setOnClickListener { toggleControls() }
        binding.incScrollSpeedButton.setOnClickListener { increaseSpeed() }
        binding.decScrollSpeedButton.setOnClickListener { decreaseSpeed() }
        binding.incScrollSpeedButton.setOnLongClickListener { startRepeatingSpeedChange(isIncreasing = true) }
        binding.decScrollSpeedButton.setOnLongClickListener { startRepeatingSpeedChange(isIncreasing = false) }
        binding.reverseScrollDirectionButton.setOnClickListener { scrollBy = -scrollBy }
        binding.toggleAutoScrollButton.setOnClickListener { toggleAutoScroll() }
    }

    override fun setSpeed(speed: Int) {
        setScrollBy(-Preferences.AUTO_SCROLL_UNIT * speed.coerceAtLeast(1), notify = false)
    }

    override fun stop() {
        binding.toggleAutoScrollButton.setIconResource(R.drawable.ic_play_arrow)
        autoScrollHandler.removeCallbacksAndMessages(null)
        interactionPointerCount = 0
        pausedByInteraction = false
        pdf.isAutoScrolling = false
    }

    override fun hideControls() {
        binding.autoScrollLayout.visibility = View.GONE
        binding.autoScrollSpeedText.visibility = View.GONE
        pdf.isAutoScrollClicked = false
    }

    override fun handleUserInteraction(motionEvent: MotionEvent) {
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> pauseForInteraction(motionEvent)

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> resumeAfterInteraction(motionEvent)
        }
    }

    private fun toggleControls() {
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
        pdf.isAutoScrollClicked = true
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
        pdf.isAutoScrolling = !pdf.isAutoScrolling

        if (!pdf.isAutoScrolling) {
            stop()
            return
        }

        binding.toggleAutoScrollButton.setIconResource(R.drawable.ic_pause)
        start()
    }

    private fun start() {
        autoScrollHandler.removeCallbacksAndMessages(null)
        scheduleTick()
    }

    private fun scheduleTick() {
        autoScrollHandler.postDelayed({
            if (!pdf.isAutoScrolling || pausedByInteraction) {
                return@postDelayed
            }
            if (!shouldContinueAutoScrolling(scrollBy)) {
                stop()
                return@postDelayed
            }

            if (preferences.getHorizontalScroll()) {
                binding.pdfView.moveRelativeTo(scrollBy.toFloat(), 0F)
            }
            else {
                binding.pdfView.moveRelativeTo(0F, scrollBy.toFloat())
            }
            binding.pdfView.loadPages()

            if (pdf.isAutoScrolling && shouldContinueAutoScrolling(scrollBy)) {
                scheduleTick()
            }
            else if (pdf.isAutoScrolling) {
                stop()
            }
        }, AUTO_SCROLL_DELAY)
    }

    private fun pauseForInteraction(motionEvent: MotionEvent) {
        interactionPointerCount = motionEvent.pointerCount
        if (!pdf.isAutoScrolling) {
            return
        }

        autoScrollHandler.removeCallbacksAndMessages(null)
        pausedByInteraction = true
    }

    private fun resumeAfterInteraction(motionEvent: MotionEvent) {
        interactionPointerCount = when (motionEvent.actionMasked) {
            MotionEvent.ACTION_POINTER_UP -> (motionEvent.pointerCount - 1).coerceAtLeast(0)
            else -> 0
        }
        if (interactionPointerCount > 0 || !pausedByInteraction || !pdf.isAutoScrolling) {
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
        scrollBy = newScrollBy
        val speed = simplifySpeed(scrollBy)
        binding.autoScrollSpeedText.text = speed.toString()
        if (notify) {
            pdf.autoScrollSpeed = speed
            onSpeedChanged(speed)
        }
    }

    private fun simplifySpeed(scrollBy: Double): Int {
        return (scrollBy.absoluteValue * (1 / Preferences.AUTO_SCROLL_UNIT)).toInt()
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
