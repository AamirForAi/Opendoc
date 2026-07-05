package com.gitlab.mudlej.MjPdfReader.manager.autoscroll

import android.view.MotionEvent

interface AutoScrollManager {

    fun setup()

    fun setSpeed(speed: Int)

    fun stop()

    fun hideControls()

    fun handleUserInteraction(motionEvent: MotionEvent)
}
