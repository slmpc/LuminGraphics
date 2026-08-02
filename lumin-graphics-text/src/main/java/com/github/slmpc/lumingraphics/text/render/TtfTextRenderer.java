package com.github.slmpc.lumingraphics.text.render;
import com.github.slmpc.lumingraphics.text.font.FontClosedException;
import com.github.slmpc.lumingraphics.text.atlas.TtfFontLoader;
import com.github.slmpc.lumingraphics.text.layout.TextLayout;
import com.github.slmpc.lumingraphics.text.layout.TextLayoutEngine;
import com.github.slmpc.lumingraphics.text.layout.TextMeasurement;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** MIG-TEXT-TTF-RENDERER */
public final class TtfTextRenderer implements TextRenderer {
    private final TextLayoutEngine layouts;
    private final TextBatchSink sink;
    private final float scaleMultiplier;
    private static final LuminColor WHITE = new LuminColor(1, 1, 1, 1);
    private final List<TextDraw> pending = new ArrayList<>();
    private boolean closed;

    public TtfTextRenderer(TextBatchSink sink) { this(new TextLayoutEngine(), 1.0f, sink); }
    /** 创建使用指定 UI 基准倍率的文本渲染器；倍率同时作用于测量和字形布局。 */
    public TtfTextRenderer(float scaleMultiplier, TextBatchSink sink) {
        this(new TextLayoutEngine(), scaleMultiplier, sink);
    }
    public TtfTextRenderer(TextLayoutEngine layouts, TextBatchSink sink) {
        this(layouts, 1.0f, sink);
    }
    public TtfTextRenderer(TextLayoutEngine layouts, float scaleMultiplier, TextBatchSink sink) {
        this.layouts = Objects.requireNonNull(layouts, "layouts");
        if (!Float.isFinite(scaleMultiplier) || scaleMultiplier <= 0.0f) {
            throw new IllegalArgumentException("scaleMultiplier must be positive and finite");
        }
        this.scaleMultiplier = scaleMultiplier;
        this.sink = Objects.requireNonNull(sink, "sink");
    }
    @Override public synchronized TextMeasurement measure(String text, float scale, TtfFontLoader font) {
        ensureOpen(); return layouts.measure(text, effectiveScale(scale), font);
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
        TextLayout layout = layouts.layout(text, x, y, effectiveScale(scale), font);
        pending.add(new TextDraw(x, y, scale, color, originX, originY, rotationDegrees, layout));
        return layout;
    }

    private float effectiveScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0.0f) {
            throw new IllegalArgumentException("scale must be positive and finite");
        }
        float effective = scale * scaleMultiplier;
        if (!Float.isFinite(effective) || effective <= 0.0f) {
            throw new IllegalArgumentException("effective text scale must be positive and finite");
        }
        return effective;
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
