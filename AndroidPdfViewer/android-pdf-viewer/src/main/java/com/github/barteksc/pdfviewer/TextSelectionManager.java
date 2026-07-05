package com.github.barteksc.pdfviewer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.github.barteksc.pdfviewer.util.TextDirectionUtil;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

final class TextSelectionManager {

    private static final int DEFAULT_SELECTION_COLOR = 0x663F51B5;
    private static final float INITIAL_HIT_TOLERANCE_PT = 2f;
    private static final float HANDLE_HIT_TOLERANCE_PT = 20f;
    private static final float VERTICAL_OVERLAP_THRESHOLD = 0.8f;
    private static final float MIN_RUN_GAP_PT = 12f;

    private enum Handle { NONE, START, END }

    private final PDFView pdfView;
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] boxScratch = new float[4];
    private final List<RectF> pdfRunRects = new ArrayList<>();
    private final float handleRadius;
    private final float handleTouchRadius;
    private final float handleStemLength;

    private boolean enabled;
    private TextSelection selection;
    private int activePage = -1;
    private Handle dragging = Handle.NONE;

    TextSelectionManager(PDFView pdfView) {
        this.pdfView = pdfView;
        float density = pdfView.getResources().getDisplayMetrics().density;
        handleRadius = 6f * density;
        handleTouchRadius = 20f * density;
        handleStemLength = 14f * density;
        selectionPaint.setStyle(Paint.Style.FILL);
        selectionPaint.setColor(DEFAULT_SELECTION_COLOR);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setStrokeWidth(2f * density);
        handlePaint.setColor(0xFF3F51B5);
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            clear();
        }
    }

    void setSelectionColor(int color) {
        selectionPaint.setColor((0x66 << 24) | (color & 0x00FFFFFF));
        handlePaint.setColor((0xFF << 24) | (color & 0x00FFFFFF));
    }

    boolean isEnabled() {
        return enabled;
    }

    boolean hasSelection() {
        return selection != null && !selection.isEmpty();
    }

    boolean isDraggingHandle() {
        return dragging != Handle.NONE;
    }

    boolean handleTouch(MotionEvent event) {
        if (!enabled || selection == null) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return beginHandleDrag(event.getX(), event.getY());
            case MotionEvent.ACTION_MOVE:
                if (dragging != Handle.NONE) {
                    moveHandle(event.getX(), event.getY());
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging != Handle.NONE) {
                    endHandleDrag();
                    return true;
                }
                return false;
            default:
                return dragging != Handle.NONE;
        }
    }

    boolean handleSingleTap(float viewX, float viewY) {
        if (selection == null) {
            return false;
        }
        if (!containsSelectionPoint(viewX, viewY) && handleHitTest(viewX, viewY) == Handle.NONE) {
            clear();
        }
        return true;
    }

    boolean startWordSelectionAt(float viewX, float viewY) {
        PdfFile pdfFile = pdfView.pdfFile;
        if (!enabled || pdfFile == null) {
            return false;
        }

        float docX = -pdfView.getCurrentXOffset() + viewX;
        float docY = -pdfView.getCurrentYOffset() + viewY;
        int page = pdfFile.getPageAtOffset(pdfView.isSwipeVertical() ? docY : docX, pdfView.getZoom());
        if (page < 0 || page >= pdfFile.getPagesCount()) {
            return false;
        }

        clear();
        pdfFile.ensureTextPage(page);
        activePage = page;
        if (pdfFile.pageCharCount(page) <= 0) {
            clear();
            return false;
        }

        PointF point = pdfFile.documentToPdf(page, pdfView.getZoom(), docX, docY);
        int glyph = pdfFile.charIndexAtPagePoint(page, point.x, point.y, INITIAL_HIT_TOLERANCE_PT);
        if (glyph < 0) {
            clear();
            return false;
        }

        int[] word = expandWord(page, glyph);
        selection = new TextSelection();
        selection.pageIndex = page;
        selection.baseChar = word[0];
        selection.extentChar = word[1];
        rebuildRects();
        if (pdfRunRects.isEmpty()) {
            clear();
            return false;
        }
        notifyChanged();
        pdfView.invalidate();
        return hasSelection();
    }

    String getSelectedText() {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null || selection == null || selection.isEmpty()) {
            return "";
        }
        String raw = pdfFile.textRange(selection.pageIndex, selection.startChar(), selection.count());
        return normalizeCopiedText(raw);
    }

    PDFView.HighlightRequest getHighlightRequest() {
        if (selection == null || selection.isEmpty() || pdfRunRects.isEmpty()) {
            return null;
        }
        List<RectF> rects = new ArrayList<>(pdfRunRects.size());
        for (RectF pdfRect : pdfRunRects) {
            rects.add(new RectF(pdfRect));
        }
        return new PDFView.HighlightRequest(selection.pageIndex, rects, getSelectedText());
    }

    RectF getSelectionViewBounds() {
        if (selection == null || pdfRunRects.isEmpty() || pdfView.pdfFile == null) {
            return null;
        }

        RectF union = null;
        float zoom = pdfView.getZoom();
        for (RectF pdfRect : pdfRunRects) {
            RectF docRect = pdfView.pdfFile.pdfRectToDocument(
                    selection.pageIndex,
                    zoom,
                    pdfRect.left,
                    pdfRect.bottom,
                    pdfRect.right,
                    pdfRect.top
            );
            if (docRect == null) {
                continue;
            }
            docRect.offset(pdfView.getCurrentXOffset(), pdfView.getCurrentYOffset());
            if (union == null) {
                union = docRect;
            } else {
                union.union(docRect);
            }
        }
        return union;
    }

    void clear() {
        boolean hadSelection = selection != null;
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile != null && activePage >= 0) {
            pdfFile.closeTextPage(activePage);
        }
        activePage = -1;
        selection = null;
        dragging = Handle.NONE;
        pdfRunRects.clear();
        if (hadSelection) {
            pdfView.callbacks.callOnTextSelectionCleared();
        }
        pdfView.invalidate();
    }

    void recycle() {
        boolean hadSelection = selection != null;
        selection = null;
        activePage = -1;
        dragging = Handle.NONE;
        pdfRunRects.clear();
        if (hadSelection) {
            pdfView.callbacks.callOnTextSelectionCleared();
        }
        if (pdfView.pdfFile != null) {
            pdfView.pdfFile.closeAllTextPages();
        }
    }

    void draw(Canvas canvas) {
        if (selection == null || pdfRunRects.isEmpty() || pdfView.pdfFile == null) {
            return;
        }

        float zoom = pdfView.getZoom();
        for (RectF pdfRect : pdfRunRects) {
            RectF docRect = pdfView.pdfFile.pdfRectToDocument(
                    selection.pageIndex,
                    zoom,
                    pdfRect.left,
                    pdfRect.bottom,
                    pdfRect.right,
                    pdfRect.top
            );
            if (docRect != null) {
                canvas.drawRect(docRect, selectionPaint);
            }
        }
        drawHandles(canvas, zoom);
    }

    private void drawHandles(Canvas canvas, float zoom) {
        drawHandle(canvas, Handle.START, zoom);
        drawHandle(canvas, Handle.END, zoom);
    }

    private void drawHandle(Canvas canvas, Handle handle, float zoom) {
        RectF docRect = handleRunDocumentRect(handle, zoom);
        if (docRect == null) {
            return;
        }

        float x = handleAnchorX(handle, docRect);
        float stemTop = docRect.bottom;
        float stemBottom = stemTop + handleStemLength;
        float circleY = stemBottom + handleRadius;
        canvas.drawLine(x, stemTop, x, stemBottom, handlePaint);
        canvas.drawCircle(x, circleY, handleRadius, handlePaint);
    }

    private int caretAt(int page, float pdfX, float pdfY, float tolerance) {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null) {
            return -1;
        }

        int glyph = pdfFile.charIndexAtPagePoint(page, pdfX, pdfY, tolerance);
        if (glyph < 0) {
            return -1;
        }
        if (!charBox(page, glyph, boxScratch)) {
            return glyph;
        }

        float centerX = (boxScratch[0] + boxScratch[2]) * 0.5f;
        boolean afterHalf = pdfX > centerX;
        if (TextDirectionUtil.isRtl(pdfFile.charUnicode(page, glyph))) {
            afterHalf = !afterHalf;
        }
        return afterHalf ? glyph + 1 : glyph;
    }

    private boolean beginHandleDrag(float viewX, float viewY) {
        Handle handle = handleHitTest(viewX, viewY);
        if (handle == Handle.NONE || selection == null) {
            return false;
        }

        dragging = handle;
        if (handle == Handle.START) {
            selection.baseChar = selection.endChar();
            selection.extentChar = selection.startChar();
        } else {
            selection.baseChar = selection.startChar();
            selection.extentChar = selection.endChar();
        }
        return true;
    }

    private void moveHandle(float viewX, float viewY) {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null || selection == null) {
            return;
        }

        float docX = -pdfView.getCurrentXOffset() + viewX;
        float docY = -pdfView.getCurrentYOffset() + viewY;
        PointF point = pdfFile.documentToPdf(selection.pageIndex, pdfView.getZoom(), docX, docY);
        int caret = caretAt(selection.pageIndex, point.x, point.y, HANDLE_HIT_TOLERANCE_PT);
        if (caret < 0) {
            return;
        }

        int charCount = pdfFile.pageCharCount(selection.pageIndex);
        caret = clamp(caret, 0, charCount);
        if (charCount > 0) {
            if (dragging == Handle.START && caret >= selection.baseChar) {
                caret = selection.baseChar > 0 ? selection.baseChar - 1 : selection.baseChar + 1;
            } else if (dragging == Handle.END && caret <= selection.baseChar) {
                caret = selection.baseChar < charCount ? selection.baseChar + 1 : selection.baseChar - 1;
            }
            caret = clamp(caret, 0, charCount);
        }

        int previousExtent = selection.extentChar;
        selection.extentChar = caret;
        rebuildRects();
        if (pdfRunRects.isEmpty()) {
            selection.extentChar = previousExtent;
            rebuildRects();
        } else {
            notifyChanged();
            pdfView.invalidate();
        }
    }

    private void notifyChanged() {
        if (selection == null || selection.isEmpty()) {
            return;
        }
        pdfView.callbacks.callOnTextSelectionChanged(
                getSelectionViewBounds(),
                selection.pageIndex
        );
    }

    private void endHandleDrag() {
        dragging = Handle.NONE;
    }

    private Handle handleHitTest(float viewX, float viewY) {
        Handle bestHandle = Handle.NONE;
        float bestDistance = Float.MAX_VALUE;
        PointF start = handleCenterView(Handle.START);
        if (start != null) {
            float distance = squaredDistance(viewX, viewY, start.x, start.y);
            if (distance <= handleTouchRadius * handleTouchRadius) {
                bestHandle = Handle.START;
                bestDistance = distance;
            }
        }

        PointF end = handleCenterView(Handle.END);
        if (end != null) {
            float distance = squaredDistance(viewX, viewY, end.x, end.y);
            if (distance <= handleTouchRadius * handleTouchRadius && distance < bestDistance) {
                bestHandle = Handle.END;
            }
        }
        return bestHandle;
    }

    private PointF handleCenterView(Handle handle) {
        RectF docRect = handleRunDocumentRect(handle, pdfView.getZoom());
        if (docRect == null) {
            return null;
        }
        float x = handleAnchorX(handle, docRect);
        float y = docRect.bottom + handleStemLength + handleRadius;
        return new PointF(x + pdfView.getCurrentXOffset(), y + pdfView.getCurrentYOffset());
    }

    private float handleAnchorX(Handle handle, RectF docRect) {
        boolean rtl = isHandleEndpointRtl(handle);
        if (handle == Handle.START) {
            return rtl ? docRect.right : docRect.left;
        }
        return rtl ? docRect.left : docRect.right;
    }

    private boolean isHandleEndpointRtl(Handle handle) {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null || selection == null) {
            return false;
        }

        int charCount = pdfFile.pageCharCount(selection.pageIndex);
        if (charCount <= 0) {
            return false;
        }

        int index = handle == Handle.START ? selection.startChar() : selection.endChar() - 1;
        index = clamp(index, 0, charCount - 1);
        return TextDirectionUtil.isRtl(pdfFile.charUnicode(selection.pageIndex, index));
    }

    private RectF handleRunDocumentRect(Handle handle, float zoom) {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null || selection == null || pdfRunRects.isEmpty()) {
            return null;
        }
        RectF pdfRect = handle == Handle.START ? pdfRunRects.get(0) : pdfRunRects.get(pdfRunRects.size() - 1);
        return pdfFile.pdfRectToDocument(
                selection.pageIndex,
                zoom,
                pdfRect.left,
                pdfRect.bottom,
                pdfRect.right,
                pdfRect.top
        );
    }

    private boolean containsSelectionPoint(float viewX, float viewY) {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null || selection == null) {
            return false;
        }
        for (RectF pdfRect : pdfRunRects) {
            RectF docRect = pdfFile.pdfRectToDocument(
                    selection.pageIndex,
                    pdfView.getZoom(),
                    pdfRect.left,
                    pdfRect.bottom,
                    pdfRect.right,
                    pdfRect.top
            );
            if (docRect == null) {
                continue;
            }
            docRect.offset(pdfView.getCurrentXOffset(), pdfView.getCurrentYOffset());
            if (docRect.contains(viewX, viewY)) {
                return true;
            }
        }
        return false;
    }

    private boolean charBox(int page, int index, float[] out) {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null) {
            return false;
        }
        if (pdfFile.looseCharBox(page, index, out) && out[2] > out[0] && out[3] > out[1]) {
            return true;
        }
        return pdfFile.tightCharBox(page, index, out) && out[2] > out[0] && out[3] > out[1];
    }

    private int[] expandWord(int page, int glyph) {
        PdfFile pdfFile = pdfView.pdfFile;
        int charCount = pdfFile.pageCharCount(page);
        if (glyph < 0 || glyph >= charCount) {
            return new int[] {0, 0};
        }
        if (!isWordChar(pdfFile.charUnicode(page, glyph))) {
            return new int[] {glyph, glyph + 1};
        }

        int start = glyph;
        int end = glyph;
        while (start > 0 && isWordChar(pdfFile.charUnicode(page, start - 1))) {
            start--;
        }
        while (end + 1 < charCount && isWordChar(pdfFile.charUnicode(page, end + 1))) {
            end++;
        }
        return new int[] {start, end + 1};
    }

    private void rebuildRects() {
        pdfRunRects.clear();
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null || selection == null || selection.isEmpty()) {
            return;
        }

        int page = selection.pageIndex;
        int charCount = pdfFile.pageCharCount(page);
        int start = Math.max(0, Math.min(selection.startChar(), charCount));
        int end = Math.max(start, Math.min(selection.endChar(), charCount));
        RectF run = null;

        for (int i = start; i < end; i++) {
            if (!charBox(page, i, boxScratch)) {
                continue;
            }
            float left = Math.min(boxScratch[0], boxScratch[2]);
            float right = Math.max(boxScratch[0], boxScratch[2]);
            float bottom = Math.min(boxScratch[1], boxScratch[3]);
            float top = Math.max(boxScratch[1], boxScratch[3]);
            if (right <= left || top <= bottom) {
                continue;
            }

            if (run == null) {
                run = pdfRect(left, bottom, right, top);
            } else if (sameRun(run, left, bottom, right, top)) {
                unionPdfRect(run, left, bottom, right, top);
            } else {
                pdfRunRects.add(run);
                run = pdfRect(left, bottom, right, top);
            }
        }

        if (run != null) {
            pdfRunRects.add(run);
        }
    }

    private RectF pdfRect(float left, float bottom, float right, float top) {
        RectF rect = new RectF();
        rect.left = left;
        rect.top = top;
        rect.right = right;
        rect.bottom = bottom;
        return rect;
    }

    private void unionPdfRect(RectF run, float left, float bottom, float right, float top) {
        run.left = Math.min(run.left, left);
        run.right = Math.max(run.right, right);
        run.top = Math.max(run.top, top);
        run.bottom = Math.min(run.bottom, bottom);
    }

    private boolean sameRun(RectF run, float left, float bottom, float right, float top) {
        float overlap = Math.min(run.top, top) - Math.max(run.bottom, bottom);
        float smallerHeight = Math.min(run.top - run.bottom, top - bottom);
        if (smallerHeight <= 0 || overlap / smallerHeight < VERTICAL_OVERLAP_THRESHOLD) {
            return false;
        }

        float gap;
        if (left > run.right) {
            gap = left - run.right;
        } else if (run.left > right) {
            gap = run.left - right;
        } else {
            gap = 0;
        }
        float maxGap = Math.max(MIN_RUN_GAP_PT, (right - left) * 3f);
        return gap <= maxGap;
    }

    private String normalizeCopiedText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replace('\uFFFE', '-')
                .replace("\u200B", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private boolean isWordChar(int codePoint) {
        return Character.isLetterOrDigit(codePoint)
                || codePoint == '_';
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private float squaredDistance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return dx * dx + dy * dy;
    }
}
