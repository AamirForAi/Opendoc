package com.github.barteksc.pdfviewer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.shockwave.pdfium.util.SizeF;

import java.util.ArrayList;
import java.util.List;

final class StampPlacementManager {

    private static final float MIN_WIDTH_PAGE_FRACTION = 0.05f;
    private static final int ACCENT_COLOR = 0xFF3F51B5;
    private static final float HANDLE_RADIUS_DP = 6f;
    private static final float HANDLE_TOUCH_RADIUS_DP = 22f;
    private static final float BORDER_STROKE_WIDTH_DP = 1.5f;
    private static final float BORDER_DASH_LENGTH_DP = 6f;
    private static final float BORDER_GAP_LENGTH_DP = 4f;
    private static final int STROKE_HEADER_FLOATS = 2;
    private static final int FLOATS_PER_SEGMENT = 6;
    private static final int COLOR_CHANNEL_MAX = 255;

    private enum DragMode { NONE, MOVE, RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR }

    private static final class CommittedStamp {
        final int pageIndex;
        final RectF pdfRect;
        final Path normalizedPath;
        final float aspect;
        final float normalizedStrokeWidth;
        final int color;

        CommittedStamp(int pageIndex, RectF pdfRect, Path normalizedPath, float aspect,
                       float normalizedStrokeWidth, int color) {
            this.pageIndex = pageIndex;
            this.pdfRect = pdfRect;
            this.normalizedPath = normalizedPath;
            this.aspect = aspect;
            this.normalizedStrokeWidth = normalizedStrokeWidth;
            this.color = color;
        }
    }

    private final PDFView pdfView;
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final Path scratchPath = new Path();
    private final float handleRadius;
    private final float handleTouchRadius;
    private final List<CommittedStamp> committedStamps = new ArrayList<>();

    private boolean active;
    private int pageIndex = -1;
    private final RectF pdfRect = new RectF();
    private float[][] strokes;
    private int color = Color.BLACK;
    private float normalizedStrokeWidth;
    private float aspect = 1f;
    private Path normalizedPath;
    private DragMode dragMode = DragMode.NONE;
    private float lastDocX;
    private float lastDocY;

    StampPlacementManager(PDFView pdfView) {
        this.pdfView = pdfView;
        float density = pdfView.getResources().getDisplayMetrics().density;
        handleRadius = HANDLE_RADIUS_DP * density;
        handleTouchRadius = HANDLE_TOUCH_RADIUS_DP * density;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(BORDER_STROKE_WIDTH_DP * density);
        borderPaint.setColor(ACCENT_COLOR);
        borderPaint.setPathEffect(new DashPathEffect(
                new float[]{BORDER_DASH_LENGTH_DP * density, BORDER_GAP_LENGTH_DP * density}, 0));
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(ACCENT_COLOR);
    }

    void start(int pageIndex, RectF rect, float[][] strokes, int color, float normalizedStrokeWidth) {
        if (rect == null || rect.width() <= 0 || strokes == null || strokes.length == 0) {
            return;
        }
        this.pageIndex = pageIndex;
        this.pdfRect.set(rect);
        this.strokes = strokes;
        this.color = color;
        this.normalizedStrokeWidth = normalizedStrokeWidth;
        this.aspect = Math.abs(rect.height()) / rect.width();
        this.normalizedPath = buildPath(strokes);
        this.dragMode = DragMode.NONE;
        this.active = true;
    }

    void cancel() {
        active = false;
        strokes = null;
        normalizedPath = null;
        dragMode = DragMode.NONE;
        pageIndex = -1;
    }

    boolean hasPending() {
        return active;
    }

    int getPendingPageIndex() {
        return pageIndex;
    }

    RectF getPendingRect() {
        return new RectF(pdfRect);
    }

    float[][] getPendingStrokes() {
        return strokes;
    }

    int getPendingColor() {
        return color;
    }

    float getPendingNormalizedStrokeWidth() {
        return normalizedStrokeWidth;
    }

    boolean isDragging() {
        return dragMode != DragMode.NONE;
    }

    void registerCommitted(int pageIndex, RectF rect, float[][] strokes, int color, float normalizedStrokeWidth) {
        if (rect == null || rect.width() <= 0 || strokes == null || strokes.length == 0) {
            return;
        }
        float stampAspect = Math.abs(rect.height()) / rect.width();
        committedStamps.add(new CommittedStamp(pageIndex, new RectF(rect), buildPath(strokes),
                stampAspect, normalizedStrokeWidth, color));
    }

    void recycle() {
        cancel();
        committedStamps.clear();
    }

    void draw(Canvas canvas) {
        if (!pdfView.isAnnotationRendering()) {
            for (CommittedStamp stamp : committedStamps) {
                RectF docRect = docRectFor(stamp.pageIndex, stamp.pdfRect);
                if (docRect == null || docRect.width() <= 0) {
                    continue;
                }
                drawStamp(canvas, stamp.normalizedPath, docRect, stamp.aspect,
                        stamp.normalizedStrokeWidth, stamp.color);
            }
        }
        if (!active || normalizedPath == null) {
            return;
        }
        RectF docRect = docRectFor(pageIndex, pdfRect);
        if (docRect == null || docRect.width() <= 0) {
            return;
        }
        drawStamp(canvas, normalizedPath, docRect, aspect, normalizedStrokeWidth, color);
        canvas.drawRect(docRect, borderPaint);
        canvas.drawCircle(docRect.left, docRect.top, handleRadius, handlePaint);
        canvas.drawCircle(docRect.right, docRect.top, handleRadius, handlePaint);
        canvas.drawCircle(docRect.left, docRect.bottom, handleRadius, handlePaint);
        canvas.drawCircle(docRect.right, docRect.bottom, handleRadius, handlePaint);
    }

    private void drawStamp(Canvas canvas, Path path, RectF docRect, float stampAspect,
                           float strokeWidth, int stampColor) {
        if (stampAspect <= 0) {
            return;
        }
        matrix.setScale(docRect.width(), docRect.height() / stampAspect);
        matrix.postTranslate(docRect.left, docRect.top);
        path.transform(matrix, scratchPath);
        strokePaint.setStrokeWidth(strokeWidth * docRect.width());
        strokePaint.setColor(pdfView.isNightModeEnabled() ? invertColor(stampColor) : stampColor);
        canvas.drawPath(scratchPath, strokePaint);
    }

    boolean handleTouch(MotionEvent event) {
        if (!active) {
            return false;
        }
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null) {
            return false;
        }
        float docX = -pdfView.getCurrentXOffset() + event.getX();
        float docY = -pdfView.getCurrentYOffset() + event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return beginDrag(docX, docY);
            case MotionEvent.ACTION_MOVE:
                if (dragMode != DragMode.NONE) {
                    moveDrag(docX, docY);
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragMode != DragMode.NONE) {
                    dragMode = DragMode.NONE;
                    return true;
                }
                return false;
            default:
                return dragMode != DragMode.NONE;
        }
    }

    private boolean beginDrag(float docX, float docY) {
        RectF docRect = docRectFor(pageIndex, pdfRect);
        if (docRect == null || docRect.width() <= 0) {
            return false;
        }
        if (hitsCorner(docX, docY, docRect.left, docRect.top)) {
            dragMode = DragMode.RESIZE_TL;
        } else if (hitsCorner(docX, docY, docRect.right, docRect.top)) {
            dragMode = DragMode.RESIZE_TR;
        } else if (hitsCorner(docX, docY, docRect.left, docRect.bottom)) {
            dragMode = DragMode.RESIZE_BL;
        } else if (hitsCorner(docX, docY, docRect.right, docRect.bottom)) {
            dragMode = DragMode.RESIZE_BR;
        } else if (docRect.contains(docX, docY)) {
            dragMode = DragMode.MOVE;
        } else {
            return false;
        }
        lastDocX = docX;
        lastDocY = docY;
        return true;
    }

    private boolean hitsCorner(float docX, float docY, float cornerX, float cornerY) {
        float dx = docX - cornerX;
        float dy = docY - cornerY;
        return dx * dx + dy * dy <= handleTouchRadius * handleTouchRadius;
    }

    private void moveDrag(float docX, float docY) {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null) {
            return;
        }
        RectF docRect = docRectFor(pageIndex, pdfRect);
        if (docRect == null || docRect.width() <= 0 || docRect.height() <= 0) {
            return;
        }
        SizeF pageSize = pdfFile.getPagePointSize(pageIndex);
        float pageWidth = pageSize.getWidth();
        float pageHeight = pageSize.getHeight();
        if (pageWidth <= 0 || pageHeight <= 0) {
            return;
        }
        float scaleX = pdfRect.width() / docRect.width();
        float scaleY = Math.abs(pdfRect.height()) / docRect.height();
        float dxPdf = (docX - lastDocX) * scaleX;
        float dyPdf = -(docY - lastDocY) * scaleY;

        if (dragMode == DragMode.MOVE) {
            float clampedDx = clamp(dxPdf, -pdfRect.left, pageWidth - pdfRect.right);
            float clampedDy = clamp(dyPdf, -pdfRect.bottom, pageHeight - pdfRect.top);
            pdfRect.offset(clampedDx, clampedDy);
        } else {
            resize(dxPdf, pageWidth, pageHeight);
        }
        lastDocX = docX;
        lastDocY = docY;
        pdfView.invalidate();
    }

    private void resize(float dxPdf, float pageWidth, float pageHeight) {
        float minWidth = MIN_WIDTH_PAGE_FRACTION * pageWidth;
        float width = pdfRect.width();
        float newWidth;
        float maxWidth;
        switch (dragMode) {
            case RESIZE_TL:
                newWidth = width - dxPdf;
                maxWidth = Math.min(pdfRect.right, (pageHeight - pdfRect.bottom) / aspect);
                newWidth = clamp(newWidth, minWidth, maxWidth);
                pdfRect.left = pdfRect.right - newWidth;
                pdfRect.top = pdfRect.bottom + newWidth * aspect;
                break;
            case RESIZE_TR:
                newWidth = width + dxPdf;
                maxWidth = Math.min(pageWidth - pdfRect.left, (pageHeight - pdfRect.bottom) / aspect);
                newWidth = clamp(newWidth, minWidth, maxWidth);
                pdfRect.right = pdfRect.left + newWidth;
                pdfRect.top = pdfRect.bottom + newWidth * aspect;
                break;
            case RESIZE_BL:
                newWidth = width - dxPdf;
                maxWidth = Math.min(pdfRect.right, pdfRect.top / aspect);
                newWidth = clamp(newWidth, minWidth, maxWidth);
                pdfRect.left = pdfRect.right - newWidth;
                pdfRect.bottom = pdfRect.top - newWidth * aspect;
                break;
            case RESIZE_BR:
                newWidth = width + dxPdf;
                maxWidth = Math.min(pageWidth - pdfRect.left, pdfRect.top / aspect);
                newWidth = clamp(newWidth, minWidth, maxWidth);
                pdfRect.right = pdfRect.left + newWidth;
                pdfRect.bottom = pdfRect.top - newWidth * aspect;
                break;
            default:
                break;
        }
    }

    private RectF docRectFor(int page, RectF rect) {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null) {
            return null;
        }
        return pdfFile.pdfRectToDocument(page, pdfView.getZoom(),
                rect.left, rect.bottom, rect.right, rect.top, false);
    }

    private static int invertColor(int color) {
        return Color.rgb(COLOR_CHANNEL_MAX - Color.red(color),
                COLOR_CHANNEL_MAX - Color.green(color),
                COLOR_CHANNEL_MAX - Color.blue(color));
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static Path buildPath(float[][] strokes) {
        Path path = new Path();
        for (float[] stroke : strokes) {
            if (stroke == null || stroke.length < STROKE_HEADER_FLOATS
                    || (stroke.length - STROKE_HEADER_FLOATS) % FLOATS_PER_SEGMENT != 0) {
                continue;
            }
            path.moveTo(stroke[0], stroke[1]);
            for (int k = STROKE_HEADER_FLOATS;
                    k + FLOATS_PER_SEGMENT - 1 < stroke.length; k += FLOATS_PER_SEGMENT) {
                path.cubicTo(stroke[k], stroke[k + 1], stroke[k + 2], stroke[k + 3],
                        stroke[k + 4], stroke[k + 5]);
            }
        }
        return path;
    }
}
