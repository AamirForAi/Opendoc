/**
 * Copyright 2016 Bartosz Schiller
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.barteksc.pdfviewer;

import static com.github.barteksc.pdfviewer.util.Constants.Cache.CACHE_SIZE;
import static com.github.barteksc.pdfviewer.util.Constants.Cache.SNAPSHOTS_CACHE_SIZE;
import static com.github.barteksc.pdfviewer.util.Constants.Cache.THUMBNAILS_CACHE_SIZE;

import android.graphics.Bitmap;
import android.graphics.RectF;

import androidx.annotation.Nullable;

import com.github.barteksc.pdfviewer.model.PagePart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

class CacheManager {

    private final PriorityQueue<PagePart> passiveCache;

    private final PriorityQueue<PagePart> activeCache;

    private final List<PagePart> thumbnails;

    private final Object passiveActiveLock = new Object();

    private final PagePartComparator orderComparator = new PagePartComparator();

    private volatile boolean scaling = false;

    public CacheManager() {
        activeCache = new PriorityQueue<>(CACHE_SIZE, orderComparator);
        passiveCache = new PriorityQueue<>(CACHE_SIZE, orderComparator);
        thumbnails = new ArrayList<>();
    }

    public void cachePart(PagePart part) {
        synchronized (passiveActiveLock) {
            removeAndRecycleEqual(activeCache, part);
            removeAndRecycleEqual(passiveCache, part);

            if (part.isSnapshot()) {
                pruneSnapshots(part);
            }

            // If cache too big, remove and recycle
            makeAFreeSpace();

            // Then add part
            activeCache.offer(part);
        }
    }

    /**
     * Keeps at most one previous snapshot for the incoming part's page and
     * {@link com.github.barteksc.pdfviewer.util.Constants.Cache#SNAPSHOTS_CACHE_SIZE}
     * snapshots overall (leaving room for the incoming one), newest first.
     */
    private void pruneSnapshots(PagePart incoming) {
        List<PagePart> snapshots = new ArrayList<>();
        collectSnapshots(activeCache, snapshots);
        collectSnapshots(passiveCache, snapshots);
        Collections.sort(snapshots, orderComparator);

        int samePageKept = 0;
        int totalKept = 0;
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            PagePart snapshot = snapshots.get(i);
            boolean samePage = snapshot.getPage() == incoming.getPage();
            boolean keep = totalKept < SNAPSHOTS_CACHE_SIZE - 1 && (!samePage || samePageKept < 1);
            if (!keep) {
                removeSnapshot(snapshot);
                continue;
            }
            totalKept++;
            if (samePage) {
                samePageKept++;
            }
        }
    }

    private void removeSnapshot(PagePart snapshot) {
        if (activeCache.remove(snapshot) || passiveCache.remove(snapshot)) {
            recycleBitmap(snapshot);
        }
    }

    private static void collectSnapshots(Collection<PagePart> parts, List<PagePart> into) {
        for (PagePart part : parts) {
            if (part.isSnapshot()) {
                into.add(part);
            }
        }
    }

    public void invalidatePageParts(int page) {
        synchronized (passiveActiveLock) {
            markPageStale(passiveCache, page);
            markPageStale(activeCache, page);
        }
        synchronized (thumbnails) {
            markPageStale(thumbnails, page);
        }
    }

    private static void markPageStale(Collection<PagePart> parts, int page) {
        for (PagePart part : parts) {
            if (part.getPage() == page) {
                part.markStale();
            }
        }
    }

    private static void removeAndRecycleEqual(PriorityQueue<PagePart> queue, PagePart newPart) {
        PagePart existing = find(queue, newPart);
        if (existing != null) {
            queue.remove(existing);
            recycleBitmap(existing);
        }
    }

    private static void recycleBitmap(@Nullable PagePart part) {
        if (part == null) {
            return;
        }
        Bitmap bitmap = part.getRenderedBitmap();
        if (bitmap == null) {
            return;
        }
        synchronized (bitmap) {
            bitmap.recycle();
        }
    }

    public void makeANewSet() {
        synchronized (passiveActiveLock) {
            passiveCache.addAll(activeCache);
            activeCache.clear();
        }
    }

    public void setScaling(boolean scaling) {
        this.scaling = scaling;
    }

    private void makeAFreeSpace() {
        synchronized (passiveActiveLock) {
            int limit = scaling ? CACHE_SIZE * 4 : CACHE_SIZE;

            while ((activeCache.size() + passiveCache.size()) >= limit &&
                    !passiveCache.isEmpty()) {
                recycleBitmap(passiveCache.poll());
            }

            if (scaling) {
                return;
            }

            while ((activeCache.size() + passiveCache.size()) >= limit &&
                    !activeCache.isEmpty()) {
                recycleBitmap(activeCache.poll());
            }
        }
    }

    public void cacheThumbnail(PagePart part) {
        synchronized (thumbnails) {
            // If cache too big, remove and recycle
            while (thumbnails.size() >= THUMBNAILS_CACHE_SIZE) {
                recycleBitmap(thumbnails.remove(0));
            }

            // Then add thumbnail
            addWithoutDuplicates(thumbnails, part);
        }

    }

    public boolean upPartIfContained(int page, RectF pageRelativeBounds, int toOrder) {
        PagePart fakePart = new PagePart(page, null, pageRelativeBounds, false, 0);

        PagePart found;
        synchronized (passiveActiveLock) {
            if ((found = find(passiveCache, fakePart)) != null) {
                if (found.isStale()) {
                    return false;
                }
                passiveCache.remove(found);
                found.setCacheOrder(toOrder);
                activeCache.offer(found);
                return true;
            }

            found = find(activeCache, fakePart);
            return found != null && !found.isStale();
        }
    }

    /**
     * Return true if already contains the described PagePart
     */
    public boolean containsThumbnail(int page, RectF pageRelativeBounds) {
        PagePart fakePart = new PagePart(page, null, pageRelativeBounds, true, 0);
        synchronized (thumbnails) {
            for (PagePart part : thumbnails) {
                if (part.equals(fakePart)) {
                    return !part.isStale();
                }
            }
            return false;
        }
    }

    /**
     * Add part if it doesn't exist, recycle bitmap otherwise.
     * A stale duplicate is replaced by the new part instead.
     */
    private void addWithoutDuplicates(Collection<PagePart> collection, PagePart newPart) {
        Iterator<PagePart> iterator = collection.iterator();
        while (iterator.hasNext()) {
            PagePart part = iterator.next();
            if (part.equals(newPart)) {
                if (!part.isStale()) {
                    recycleBitmap(newPart);
                    return;
                }
                iterator.remove();
                recycleBitmap(part);
                break;
            }
        }
        collection.add(newPart);
    }

    @Nullable
    private static PagePart find(PriorityQueue<PagePart> vector, PagePart fakePart) {
        for (PagePart part : vector) {
            if (part.equals(fakePart)) {
                return part;
            }
        }
        return null;
    }

    public List<PagePart> getPageParts() {
        synchronized (passiveActiveLock) {
            List<PagePart> parts = new ArrayList<>(passiveCache);
            parts.addAll(activeCache);
            Collections.sort(parts, orderComparator);
            return parts;
        }
    }

    public List<PagePart> getThumbnails() {
        synchronized (thumbnails) {
            return new ArrayList<>(thumbnails);
        }
    }

    public void recycle() {
        synchronized (passiveActiveLock) {
            for (PagePart part : passiveCache) {
                recycleBitmap(part);
            }
            passiveCache.clear();
            for (PagePart part : activeCache) {
                recycleBitmap(part);
            }
            activeCache.clear();
        }
        synchronized (thumbnails) {
            for (PagePart part : thumbnails) {
                recycleBitmap(part);
            }
            thumbnails.clear();
        }
    }

    class PagePartComparator implements Comparator<PagePart> {
        @Override
        public int compare(PagePart part1, PagePart part2) {
            if (part1.getCacheOrder() == part2.getCacheOrder()) {
                return 0;
            }
            return part1.getCacheOrder() > part2.getCacheOrder() ? 1 : -1;
        }
    }

}
