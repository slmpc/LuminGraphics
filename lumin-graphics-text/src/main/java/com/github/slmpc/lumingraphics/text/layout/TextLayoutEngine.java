package com.github.slmpc.lumingraphics.text.layout;
import com.github.slmpc.lumingraphics.text.font.FontClosedException;
import com.github.slmpc.lumingraphics.text.font.FontLoader;
import com.github.slmpc.lumingraphics.text.atlas.GlyphDescriptor;
import com.github.slmpc.lumingraphics.text.atlas.TtfFontLoader;
import com.github.slmpc.lumingraphics.text.atlas.TtfGlyphAtlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TextLayoutEngine implements AutoCloseable {
    private static final int CACHE_LIMIT = 128;
    private final Map<LayoutKey, CachedLayout> cache = new LinkedHashMap<>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<LayoutKey, CachedLayout> eldest) {
            return size() > CACHE_LIMIT;
        }
    };
    private boolean closed;

    public TextMeasurement measure(String text, float scale, FontLoader font) {
        validate(text, scale, font);
        float maxWidth = 0, lineWidth = 0;
        int previous = -1, lines = 1;
        for (int offset = 0; offset < text.length();) {
            int codepoint = text.codePointAt(offset);
            offset += Character.charCount(codepoint);
            if (codepoint == '\n') {
                maxWidth = Math.max(maxWidth, lineWidth);
                lineWidth = 0;
                previous = -1;
                lines++;
                continue;
            }
            if (previous >= 0) lineWidth += font.kerning(previous, codepoint) * scale;
            lineWidth += font.advance(codepoint) * scale;
            previous = codepoint;
        }
        return new TextMeasurement(Math.max(maxWidth, lineWidth), lines * font.metrics().lineHeight() * scale, lines);
    }

    public synchronized TextLayout layout(String text, float x, float y, float scale, TtfFontLoader font) {
        validate(text, scale, font);
        ensureOpen();
        LayoutKey key = new LayoutKey(font, text, x, y, scale, font.glyphRevision(), font.atlasRevision());
        CachedLayout existing = cache.get(key);
        if (existing != null) return existing.materialize();

        TextMeasurement measurement = measure(text, scale, font);
        Map<TtfGlyphAtlas, List<GlyphPlacement>> grouped = new LinkedHashMap<>();
        float cursorX = x, baseline = y + font.metrics().ascent() * scale;
        int previous = -1;
        int glyphCount = 0;
        long hash = 0xcbf29ce484222325L;
        for (int offset = 0; offset < text.length();) {
            int codepoint = text.codePointAt(offset);
            offset += Character.charCount(codepoint);
            if (codepoint == '\n') {
                cursorX = x;
                baseline += font.metrics().lineHeight() * scale;
                previous = -1;
                continue;
            }
            if (previous >= 0) cursorX += font.kerning(previous, codepoint) * scale;
            if (codepoint == ' ' || codepoint == '\t') {
                cursorX += font.advance(codepoint) * scale;
                previous = codepoint;
                continue;
            }
            GlyphDescriptor glyph = font.requireGlyph(codepoint);
            float x0 = cursorX + glyph.xOffset() * scale;
            float y0 = baseline + glyph.yOffset() * scale;
            float x1 = x0 + glyph.width() * scale;
            float y1 = y0 + glyph.height() * scale;
            GlyphPlacement placement = new GlyphPlacement(codepoint, x0, y0, x1, y1, glyph.uv());
            grouped.computeIfAbsent(glyph.atlas(), ignored -> new ArrayList<>()).add(placement);
            hash = hashPlacement(hash, placement);
            cursorX += glyph.advance() * scale;
            previous = codepoint;
            glyphCount++;
        }
        Map<TtfGlyphAtlas, List<GlyphPlacement>> immutableGroups = new LinkedHashMap<>();
        grouped.forEach((atlas, placements) -> immutableGroups.put(atlas, List.copyOf(placements)));
        CachedLayout cached = new CachedLayout(measurement.width(), measurement.height(), glyphCount,
                font.glyphRevision(), font.atlasRevision(), hash,
                Collections.unmodifiableMap(immutableGroups));
        cache.put(new LayoutKey(font, text, x, y, scale, cached.glyphRevision(), cached.atlasRevision()), cached);
        return cached.materialize();
    }

    public static List<TextRenderBatch> acquireBatches(Map<TtfGlyphAtlas, List<GlyphPlacement>> grouped) {
        List<TextRenderBatch> batches = new ArrayList<>(grouped.size());
        try {
            for (Map.Entry<TtfGlyphAtlas, List<GlyphPlacement>> entry : grouped.entrySet()) {
                batches.add(new TextRenderBatch(entry.getKey(), entry.getValue()));
            }
            return List.copyOf(batches);
        } catch (RuntimeException | Error failure) {
            for (int index = batches.size() - 1; index >= 0; index--) {
                try { batches.get(index).close(); } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    public synchronized void clear() { cache.clear(); }

    @Override public synchronized void close() {
        if (!closed) {
            closed = true;
            cache.clear();
        }
    }

    private void ensureOpen() {
        if (closed) throw new FontClosedException("Text layout engine is closed");
    }

    private static long hashPlacement(long hash, GlyphPlacement placement) {
        hash = mix(hash, placement.codepoint());
        hash = mix(hash, Float.floatToIntBits(placement.x0()));
        hash = mix(hash, Float.floatToIntBits(placement.y0()));
        hash = mix(hash, Float.floatToIntBits(placement.x1()));
        hash = mix(hash, Float.floatToIntBits(placement.y1()));
        return hash;
    }

    private static long mix(long hash, int value) { return (hash ^ (value & 0xffffffffL)) * 0x100000001b3L; }
    private static void validate(String text, float scale, FontLoader font) {
        if (text == null || font == null) throw new NullPointerException();
        if (!Float.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("scale must be positive and finite");
    }

    private record LayoutKey(TtfFontLoader font, String text, float x, float y, float scale,
                             long glyphRevision, long atlasRevision) {}

    private record CachedLayout(float width, float height, int glyphCount, long glyphRevision, long atlasRevision,
                                long stableHash, Map<TtfGlyphAtlas, List<GlyphPlacement>> groups) {
        TextLayout materialize() {
            return new TextLayout(width, height, glyphCount, glyphRevision, atlasRevision, stableHash,
                    acquireBatches(groups));
        }
    }
}
