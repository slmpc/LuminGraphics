package com.github.slmpc.lumingraphics.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** MIG-TEXT-TTF-RENDERER */
public final class TtfTextRenderer implements TextRenderer {
    private final TextLayoutEngine layouts;
    private final TextBatchSink sink;
    private final List<TextRenderBatch> pending = new ArrayList<>();
    private boolean closed;

    public TtfTextRenderer(TextBatchSink sink) { this(new TextLayoutEngine(), sink); }
    public TtfTextRenderer(TextLayoutEngine layouts, TextBatchSink sink) {
        this.layouts = Objects.requireNonNull(layouts, "layouts");
        this.sink = Objects.requireNonNull(sink, "sink");
    }
    @Override public synchronized TextMeasurement measure(String text, float scale, TtfFontLoader font) {
        ensureOpen(); return layouts.measure(text, scale, font);
    }
    @Override public synchronized TextLayout add(String text, float x, float y, float scale, TtfFontLoader font) {
        ensureOpen();
        TextLayout layout = layouts.layout(text, x, y, scale, font);
        pending.addAll(layout.batches());
        return layout;
    }
    @Override public synchronized void draw() {
        ensureOpen();
        if (pending.isEmpty()) return;
        try {
            sink.draw(List.copyOf(pending));
        } catch (RuntimeException | Error failure) {
            try { releasePending(); } catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }
    @Override public synchronized void clear() { ensureOpen(); releasePending(); }
    private void ensureOpen() { if (closed) throw new FontClosedException("Text renderer is closed"); }
    @Override public synchronized void close() {
        if (!closed) {
            closed = true;
            try {
                releasePending();
            } finally {
                layouts.close();
            }
        }
    }

    private void releasePending() {
        RuntimeException failure = null;
        List<TextRenderBatch> snapshot = List.copyOf(pending);
        pending.clear();
        for (TextRenderBatch batch : snapshot) {
            try { batch.close(); } catch (RuntimeException error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
}
