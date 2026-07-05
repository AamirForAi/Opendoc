package com.github.barteksc.pdfviewer.listener;

import android.graphics.RectF;

public interface OnTextSelectionChangeListener {
    void onTextSelectionChanged(String selectedText, RectF viewBounds, int pageIndex);

    void onTextSelectionCleared();
}
