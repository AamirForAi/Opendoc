// Written by Mudlej. License is GPLv3.
package com.github.barteksc.pdfviewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.github.barteksc.pdfviewer.preview.PreviewBitmapAdapter;
import com.github.barteksc.pdfviewer.preview.PreviewBitmapPool;
import com.github.barteksc.pdfviewer.preview.PreviewCodec;

import java.io.ByteArrayOutputStream;

final class AndroidPreviewBitmaps implements PreviewBitmapAdapter<Bitmap>, PreviewCodec<Bitmap> {

    private static final int JPEG_QUALITY = 80;

    private final int bucketWidth;
    private volatile PreviewBitmapPool<Bitmap> pool;

    AndroidPreviewBitmaps(int bucketWidth) {
        this.bucketWidth = bucketWidth;
    }

    void attachPool(PreviewBitmapPool<Bitmap> pool) {
        this.pool = pool;
    }

    @Override
    public int byteCount(Bitmap bitmap) {
        return bitmap == null ? 0 : bitmap.getByteCount();
    }

    @Override
    public void recycle(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        synchronized (bitmap) {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    @Override
    public byte[] encode(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        Bitmap copy;
        try {
            synchronized (bitmap) {
                if (bitmap.isRecycled()) {
                    return null;
                }
                Bitmap.Config config = bitmap.getConfig();
                if (config == null) {
                    config = Bitmap.Config.RGB_565;
                }
                copy = bitmap.copy(config, false);
            }
        } catch (OutOfMemoryError e) {
            return null;
        }
        if (copy == null) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            copy.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        } finally {
            copy.recycle();
        }
        return out.toByteArray();
    }

    @Override
    public Bitmap decode(byte[] data) {
        if (data == null) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        PreviewBitmapPool<Bitmap> currentPool = pool;
        Bitmap reuse = currentPool == null ? null : currentPool.acquire(bucketWidth);
        if (reuse != null) {
            options.inMutable = true;
            options.inBitmap = reuse;
        }
        try {
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (IllegalArgumentException e) {
            if (reuse != null) {
                recycle(reuse);
            }
            options.inBitmap = null;
            options.inMutable = false;
            try {
                return BitmapFactory.decodeByteArray(data, 0, data.length, options);
            } catch (RuntimeException retry) {
                return null;
            }
        }
    }
}
