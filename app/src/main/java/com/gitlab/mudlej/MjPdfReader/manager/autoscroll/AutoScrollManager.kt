package com.gitlab.mudlej.MjPdfReader.manager.autoscroll

import android.view.MotionEvent

interface AutoScrollManager {

    fun setup()

    fun stop()

    fun hideControls()

    fun handleUserInteraction(motionEvent: MotionEvent)
}
