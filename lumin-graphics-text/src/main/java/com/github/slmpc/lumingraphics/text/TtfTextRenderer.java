package com.github.slmpc.lumingraphics.text;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** MIG-TEXT-TTF-RENDERER */
public final class TtfTextRenderer implements TextRenderer {
    private final TextLayoutEngine layouts;
    private final TextBatchSink sink;
    private static final LuminColor WHITE = new LuminColor(1, 1, 1, 1);
    private final List<TextDraw> pending = new ArrayList<>();
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
        return add(text, x, y, scale, WHITE, font);
    }
    @Override public synchronized TextLayout add(
            String text, float x, float y, float scale, LuminColor color, TtfFontLoader font) {
        return addDraw(text, x, y, scale, color, font, x, y, 0);
    }
    @Override public synchronized TextLayout addRotated(
            String text, float x, float y, float scale, LuminColor color, TtfFontLoader font,
            float originX, float originY, float rotationDegrees) {
        return addDraw(text, x, y, scale, color, font, originX, originY, rotationDegrees);
    }
    @Override public synchronized void draw() {
        ensureOpen();
        if (pending.isEmpty()) return;
        List<TextDraw> snapshot = List.copyOf(pending);
        pending.clear();
        try {
            sink.draw(snapshot);
        } catch (RuntimeException | Error failure) {
            try { closeDraws(snapshot); } catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
        closeDraws(snapshot);
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
        List<TextDraw> snapshot = List.copyOf(pending);
        pending.clear();
        closeDraws(snapshot);
    }

    private TextLayout addDraw(String text, float x, float y, float scale, LuminColor color, TtfFontLoader font,
                               float originX, float originY, float rotationDegrees) {
        ensureOpen();
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(font, "font");
        validateFinite(x, y, scale, originX, originY, rotationDegrees);
        if (scale <= 0) throw new IllegalArgumentException("scale must be positive");
        TextLayout layout = layouts.layout(text, x, y, scale, font);
        pending.add(new TextDraw(x, y, scale, color, originX, originY, rotationDegrees, layout));
        return layout;
    }

    private static void closeDraws(List<TextDraw> draws) {
        RuntimeException failure = null;
        for (TextDraw draw : draws) {
            try { draw.close(); } catch (RuntimeException error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }

    private static void validateFinite(float... values) {
        for (float value : values) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("text draw values must be finite");
        }
    }
}
