package com.github.slmpc.lumingraphics.text.ttf;

import com.github.slmpc.lumingraphics.text.font.FontClosedException;
import com.github.slmpc.lumingraphics.text.font.FontMalformedException;
import com.github.slmpc.lumingraphics.text.font.FontMetrics;
import com.github.slmpc.lumingraphics.text.font.FontResource;
import com.github.slmpc.lumingraphics.text.font.MissingGlyphException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * MIG-TEXT-TTF-FILE
 */
public final class TtfFontFile implements AutoCloseable {
    private final ByteBuffer data;
    private final STBTTFontinfo info;
    private final float scale;
    private final int padding;
    private final FontMetrics metrics;
    private boolean closed;

    private TtfFontFile(byte[] bytes, int pixelHeight, int padding, String source) {
        if (pixelHeight <= padding * 2 || padding <= 0) throw new IllegalArgumentException("Invalid font dimensions");
        int fontOffset = validateFontContainer(bytes, source);
        data = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        info = STBTTFontinfo.malloc();
        if (!STBTruetype.stbtt_InitFont(info, data, fontOffset)) {
            info.free();
            MemoryUtil.memFree(data);
            throw new FontMalformedException("STB could not parse font: " + source);
        }
        this.padding = padding;
        scale = STBTruetype.stbtt_ScaleForPixelHeight(info, pixelHeight - padding * 2.0f);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ascent = stack.mallocInt(1), descent = stack.mallocInt(1), gap = stack.mallocInt(1);
            STBTruetype.stbtt_GetFontVMetrics(info, ascent, descent, gap);
            int scaledAscent = Math.round(ascent.get(0) * scale);
            int scaledDescent = Math.round(descent.get(0) * scale);
            int scaledGap = Math.round(gap.get(0) * scale);
            metrics = new FontMetrics(scaledAscent, scaledDescent, scaledGap,
                    Math.round((ascent.get(0) - descent.get(0) + gap.get(0)) * scale));
        }
    }

    private static int validateFontContainer(byte[] bytes, String source) {
        if (bytes.length < 12) throw new FontMalformedException("Truncated font header: " + source);
        long signature = u32(bytes, 0);
        if (signature == 0x74746366L) {
            long count = u32(bytes, 8);
            if (count < 1 || bytes.length < 16) throw new FontMalformedException("Invalid TTC header: " + source);
            long offset = u32(bytes, 12);
            if (offset > Integer.MAX_VALUE) throw new FontMalformedException("Invalid TTC offset: " + source);
            validateSfnt(bytes, (int) offset, source);
            return (int) offset;
        }
        if (signature != 0x00010000L && signature != 0x4f54544fL
                && signature != 0x74727565L && signature != 0x74797031L) {
            throw new FontMalformedException("Unsupported sfnt signature: " + source);
        }
        validateSfnt(bytes, 0, source);
        return 0;
    }

    private static void validateSfnt(byte[] bytes, int offset, String source) {
        if (offset < 0 || offset > bytes.length - 12)
            throw new FontMalformedException("Truncated sfnt header: " + source);
        int tableCount = u16(bytes, offset + 4);
        long directoryEnd = (long) offset + 12L + tableCount * 16L;
        if (tableCount == 0 || directoryEnd > bytes.length) {
            throw new FontMalformedException("Truncated sfnt table directory: " + source);
        }
        for (int table = 0; table < tableCount; table++) {
            int record = offset + 12 + table * 16;
            long tableOffset = u32(bytes, record + 8);
            long tableLength = u32(bytes, record + 12);
            if (tableOffset > bytes.length || tableLength > bytes.length - tableOffset) {
                throw new FontMalformedException("Invalid sfnt table bounds: " + source);
            }
        }
    }

    private static int u16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 8 | bytes[offset + 1] & 0xff;
    }

    private static long u32(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xff) << 24) | ((long) (bytes[offset + 1] & 0xff) << 16)
                | ((long) (bytes[offset + 2] & 0xff) << 8) | bytes[offset + 3] & 0xffL;
    }

    public static TtfFontFile open(FontResource resource, int pixelHeight, int padding) {
        try {
            return new TtfFontFile(resource.read(), pixelHeight, padding, resource.description());
        } catch (FontMalformedException error) {
            throw error;
        } catch (IOException | RuntimeException error) {
            throw new FontMalformedException("Unable to read font: " + resource.description(), error);
        }
    }

    public synchronized float scale() {
        ensureOpen();
        return scale;
    }

    public synchronized FontMetrics metrics() {
        ensureOpen();
        return metrics;
    }

    public synchronized boolean hasGlyph(int codepoint) {
        ensureOpen();
        return Character.isValidCodePoint(codepoint) && STBTruetype.stbtt_FindGlyphIndex(info, codepoint) != 0;
    }

    public synchronized int advance(int codepoint) {
        ensureGlyph(codepoint);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer advance = stack.mallocInt(1), bearing = stack.mallocInt(1);
            STBTruetype.stbtt_GetCodepointHMetrics(info, codepoint, advance, bearing);
            return Math.round(advance.get(0) * scale);
        }
    }

    public synchronized int kerning(int left, int right) {
        ensureGlyph(left);
        ensureGlyph(right);
        return Math.round(STBTruetype.stbtt_GetCodepointKernAdvance(info, left, right) * scale);
    }

    public synchronized int measureAdvance(String text) {
        ensureOpen();
        int width = 0, previous = -1;
        for (int offset = 0; offset < text.length(); ) {
            int codepoint = text.codePointAt(offset);
            offset += Character.charCount(codepoint);
            if (previous >= 0) width += kerning(previous, codepoint);
            width += advance(codepoint);
            previous = codepoint;
        }
        return width;
    }

    public synchronized TtfGlyph rasterize(int codepoint) {
        ensureGlyph(codepoint);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1), height = stack.mallocInt(1);
            IntBuffer xOffset = stack.mallocInt(1), yOffset = stack.mallocInt(1);
            byte edge = (byte) 128;
            ByteBuffer nativePixels = STBTruetype.stbtt_GetCodepointSDF(info, scale, codepoint, padding,
                    edge, (edge & 0xff) / (float) padding, width, height, xOffset, yOffset);
            if (nativePixels == null) throw new MissingGlyphException(codepoint);
            try {
                byte[] pixels = new byte[width.get(0) * height.get(0)];
                nativePixels.get(0, pixels);
                return new TtfGlyph(codepoint, width.get(0), height.get(0), xOffset.get(0), yOffset.get(0),
                        advance(codepoint), pixels);
            } finally {
                STBTruetype.stbtt_FreeSDF(nativePixels);
            }
        }
    }

    private void ensureGlyph(int codepoint) {
        ensureOpen();
        if (!hasGlyph(codepoint)) throw new MissingGlyphException(codepoint);
    }

    private void ensureOpen() {
        if (closed) throw new FontClosedException("TTF font is closed");
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            info.free();
            MemoryUtil.memFree(data);
        }
    }
}
