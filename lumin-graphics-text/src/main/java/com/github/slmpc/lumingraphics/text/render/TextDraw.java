package com.github.slmpc.lumingraphics.text.render;

import com.github.slmpc.lumingraphics.text.font.FontClosedException;
import com.github.slmpc.lumingraphics.text.layout.TextLayout;
import com.github.slmpc.lumingraphics.text.layout.TextRenderBatch;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Immutable metadata and retained glyph batches for one queued text add.
 * The sink borrows this value only for the duration of {@link TextBatchSink#draw(List)};
 * the renderer closes it after that call returns or fails. A delayed consumer must call
 * {@link #retain()} during that callback and own the returned draw until it flushes or clears it.
 */
public final class TextDraw implements AutoCloseable {
    private final float x;
    private final float y;
    private final float scale;
    private final LuminColor color;
    private final float originX;
    private final float originY;
    private final float rotationDegrees;
    private final TextLayout layout;
    private final AtomicBoolean closed = new AtomicBoolean();

    public TextDraw(float x, float y, float scale, LuminColor color, float originX, float originY,
                    float rotationDegrees, TextLayout layout) {
        this.x = finite("x", x);
        this.y = finite("y", y);
        this.scale = finite("scale", scale);
        if (scale <= 0) throw new IllegalArgumentException("scale must be positive");
        this.color = Objects.requireNonNull(color, "color");
        this.originX = finite("originX", originX);
        this.originY = finite("originY", originY);
        this.rotationDegrees = finite("rotationDegrees", rotationDegrees);
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float scale() {
        return scale;
    }

    public LuminColor color() {
        return color;
    }

    public float originX() {
        return originX;
    }

    public float originY() {
        return originY;
    }

    public float rotationDegrees() {
        return rotationDegrees;
    }

    public float width() {
        return layout.width();
    }

    public float height() {
        return layout.height();
    }

    public int glyphCount() {
        return layout.glyphCount();
    }

    public long glyphRevision() {
        return layout.glyphRevision();
    }

    public long atlasRevision() {
        return layout.atlasRevision();
    }

    public long stableHash() {
        return layout.stableHash();
    }

    public List<TextRenderBatch> batches() {
        ensureOpen();
        return layout.batches();
    }

    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Returns an independently owned snapshot retaining the exact uploads used by this draw.
     */
    public synchronized TextDraw retain() {
        ensureOpen();
        List<TextRenderBatch> retained = new java.util.ArrayList<>(layout.batches().size());
        try {
            for (TextRenderBatch batch : layout.batches()) retained.add(batch.retain());
        } catch (RuntimeException | Error failure) {
            closeReverse(retained, failure);
            throw failure;
        }
        TextLayout retainedLayout = new TextLayout(
                layout.width(), layout.height(), layout.glyphCount(), layout.glyphRevision(),
                layout.atlasRevision(), layout.stableHash(), retained);
        return new TextDraw(x, y, scale, color, originX, originY, rotationDegrees, retainedLayout);
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;
        RuntimeException failure = null;
        for (TextRenderBatch batch : layout.batches()) {
            try {
                batch.close();
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }

    private static void closeReverse(List<TextRenderBatch> batches, Throwable failure) {
        for (int index = batches.size() - 1; index >= 0; index--) {
            try {
                batches.get(index).close();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) throw new FontClosedException("Text draw is closed");
    }

    private static float finite(String name, float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }
}
