package com.shockwave.pdfium;

import android.graphics.RectF;
import android.os.ParcelFileDescriptor;
import android.util.ArrayMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PdfDocument {

    public static class Meta {
        String title;
        String author;
        String subject;
        String keywords;
        String creator;
        String producer;
        String creationDate;
        String modDate;
        int totalPages;

        public String getTitle() {
            return title;
        }

        public String getAuthor() {
            return author;
        }

        public String getSubject() {
            return subject;
        }

        public String getKeywords() {
            return keywords;
        }

        public String getCreator() {
            return creator;
        }

        public String getProducer() {
            return producer;
        }

        public String getCreationDate() {
            return creationDate;
        }

        public String getModDate() {
            return modDate;
        }

        public int getTotalPages() {
            return totalPages;
        }
    }

    public static class Bookmark {
        private List<Bookmark> children = new ArrayList<>();
        public String title;
        public long pageIdx;
        public long mNativePtr;

        public List<Bookmark> getChildren() {
            return children;
        }

        public void setChildren(List<Bookmark> newChildren) {
            children.addAll(newChildren);
        }

        public boolean hasChildren() {
            return !children.isEmpty();
        }

        public String getTitle() {
            return title;
        }

        public long getPageIdx() {
            return pageIdx;
        }
    }

    public static class Link {
        private RectF bounds;
        private Integer destPageIdx;
        private String uri;

        public Link(RectF bounds, Integer destPageIdx, String uri) {
            this.bounds = bounds;
            this.destPageIdx = destPageIdx;
            this.uri = uri;
        }

        public Integer getDestPageIdx() {
            return destPageIdx;
        }

        public String getUri() {
            return uri;
        }

        public RectF getBounds() {
            return bounds;
        }
    }

    public static class HighlightAnnotation {
        private int annotationIndex;
        private String groupKey;
        private RectF bounds;
        private String contents;
        private int color;

        public HighlightAnnotation(int annotationIndex, String groupKey, RectF bounds, String contents) {
            this(annotationIndex, groupKey, bounds, contents, 0xFFFFFF00);
        }

        public HighlightAnnotation(int annotationIndex, String groupKey, RectF bounds, String contents, int color) {
            this.annotationIndex = annotationIndex;
            this.groupKey = groupKey == null ? "" : groupKey;
            this.bounds = bounds;
            this.contents = contents == null ? "" : contents;
            this.color = color;
        }

        public int getAnnotationIndex() {
            return annotationIndex;
        }

        public String getGroupKey() {
            return groupKey;
        }

        public RectF getBounds() {
            return bounds;
        }

        public String getContents() {
            return contents;
        }

        public int getColor() {
            return color;
        }
    }

    /*package*/ PdfDocument() {
    }

    /*package*/ long mNativeDocPtr;
    /*package*/ ParcelFileDescriptor parcelFileDescriptor;

    /*package*/ final Map<Integer, Long> mNativePagesPtr = new ArrayMap<>();
    /*package*/ final Map<Integer, Long> mNativeTextPagesPtr = new ArrayMap<>();

    public boolean hasPage(int index) {
        return mNativePagesPtr.containsKey(index);
    }
}
