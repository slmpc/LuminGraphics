package com.github.slmpc.lumingraphics.text;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** MIG-TEXT-TTF-ATLAS */
public final class TtfGlyphAtlas implements AutoCloseable {
    private final int page;
    private final int width;
    private final int height;
    private final byte[] pixels;
    private final GlyphAtlasUploader uploader;
    private int cursorX = 1;
    private int cursorY = 1;
    private int rowHeight;
    private long revision;
    private UploadSlot upload;
    private boolean closed;

    public TtfGlyphAtlas(int page, int width, int height, GlyphAtlasUploader uploader) {
        if (page < 0 || width < 2 || height < 2) throw new IllegalArgumentException("Invalid atlas dimensions");
        this.page = page;
        this.width = width;
        this.height = height;
        this.pixels = new byte[width * height];
        this.uploader = Objects.requireNonNull(uploader, "uploader");
    }

    public synchronized GlyphUv append(TtfGlyph glyph) {
        return append(glyph, () -> true);
    }

    synchronized GlyphUv append(TtfGlyph glyph, BooleanSupplier beginCommit) {
        ensureOpen();
        int glyphWidth = glyph.width(), glyphHeight = glyph.height();
        if (glyphWidth + 2 > width || glyphHeight + 2 > height) return null;
        int nextX = cursorX;
        int nextY = cursorY;
        int nextRowHeight = rowHeight;
        if (nextX + glyphWidth + 1 > width) {
            nextX = 1;
            nextY += nextRowHeight + 1;
            nextRowHeight = 0;
        }
        if (nextY + glyphHeight + 1 > height) return null;

        byte[] source = glyph.pixels();
        for (int row = 0; row < glyphHeight; row++) {
            System.arraycopy(source, row * glyphWidth, pixels, (nextY + row) * width + nextX, glyphWidth);
        }
        long nextRevision = revision + 1;
        GlyphAtlasUpload nextUpload;
        try {
            nextUpload = Objects.requireNonNull(uploader.upload(new AtlasPixels(width, height, nextRevision, pixels)),
                    "uploader result");
        } catch (RuntimeException error) {
            rollback(nextX, nextY, glyphWidth, glyphHeight);
            throw new GlyphUploadException("Failed to upload glyph atlas page " + page, error);
        }
        if (!beginCommit.getAsBoolean()) {
            rollback(nextX, nextY, glyphWidth, glyphHeight);
            nextUpload.close();
            return null;
        }
        UploadSlot previous = upload;
        upload = new UploadSlot(nextUpload);
        revision = nextRevision;
        cursorX = nextX + glyphWidth + 1;
        cursorY = nextY;
        rowHeight = Math.max(nextRowHeight, glyphHeight);
        if (previous != null) previous.release();
        return new GlyphUv(nextX / (float) width, nextY / (float) height,
                (nextX + glyphWidth) / (float) width, (nextY + glyphHeight) / (float) height);
    }

    public int page() { return page; }
    public synchronized long revision() { ensureOpen(); return revision; }
    public synchronized GlyphAtlasUpload upload() { ensureOpen(); return upload == null ? null : upload.value(); }
    synchronized UploadLease retainUpload() {
        ensureOpen();
        if (upload == null) throw new IllegalStateException("Glyph atlas has no upload");
        return upload.retain();
    }
    private void ensureOpen() { if (closed) throw new FontClosedException("Glyph atlas is closed"); }

    private void rollback(int x, int y, int glyphWidth, int glyphHeight) {
        for (int row = 0; row < glyphHeight; row++) {
            java.util.Arrays.fill(pixels, (y + row) * width + x,
                    (y + row) * width + x + glyphWidth, (byte) 0);
        }
    }

    @Override public synchronized void close() {
        if (!closed) {
            closed = true;
            UploadSlot current = upload;
            upload = null;
            if (current != null) current.release();
        }
    }

    static final class UploadLease implements AutoCloseable {
        private final UploadSlot slot;
        private final AtomicBoolean closed = new AtomicBoolean();

        private UploadLease(UploadSlot slot) { this.slot = slot; }
        GlyphAtlasUpload upload() { return slot.value(); }
        @Override public void close() { if (closed.compareAndSet(false, true)) slot.release(); }
    }

    private static final class UploadSlot {
        private final GlyphAtlasUpload upload;
        private int references = 1;

        private UploadSlot(GlyphAtlasUpload upload) { this.upload = upload; }
        synchronized GlyphAtlasUpload value() { return upload; }
        synchronized UploadLease retain() {
            if (references == 0) throw new FontClosedException("Glyph atlas upload is retired");
            references++;
            return new UploadLease(this);
        }
        synchronized void release() {
            if (references == 0) return;
            references--;
            if (references == 0) upload.close();
        }
    }
}
