package com.github.barteksc.pdfviewer.listener;

import android.view.MotionEvent;

/**
 * Called when the user starts interacting directly with the PDF document.
 */
public interface OnDocumentInteractionListener {

    void onDocumentInteraction(MotionEvent event);
}
