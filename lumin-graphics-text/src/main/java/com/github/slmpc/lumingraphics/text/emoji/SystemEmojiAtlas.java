package com.github.slmpc.lumingraphics.text.emoji;
import com.github.slmpc.lumingraphics.text.font.FontClosedException;
import com.github.slmpc.lumingraphics.text.font.MissingGlyphException;
import com.github.slmpc.lumingraphics.text.atlas.AtlasExhaustedException;
import com.github.slmpc.lumingraphics.text.atlas.AtlasPixelFormat;
import com.github.slmpc.lumingraphics.text.atlas.AtlasPixels;
import com.github.slmpc.lumingraphics.text.atlas.GlyphAtlasUpload;
import com.github.slmpc.lumingraphics.text.atlas.GlyphAtlasUploader;
import com.github.slmpc.lumingraphics.text.atlas.GlyphUploadException;
import com.github.slmpc.lumingraphics.text.atlas.GlyphUv;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** MIG-TEXT-EMOJI-ATLAS */
public final class SystemEmojiAtlas implements AutoCloseable {
    private final int width;
    private final int height;
    private final int fontSize;
    private final byte[] rgba;
    private final GlyphAtlasUploader uploader;
    private final Map<Integer, EmojiGlyph> glyphs = new LinkedHashMap<>();
    private int cursorX = 1;
    private int cursorY = 1;
    private int rowHeight;
    private long revision;
    private GlyphAtlasUpload upload;
    private boolean closed;

    public SystemEmojiAtlas(int width, int height, int fontSize, GlyphAtlasUploader uploader) {
        if (width < 2 || height < 2 || fontSize <= 0) throw new IllegalArgumentException("Invalid emoji atlas dimensions");
        this.width = width;
        this.height = height;
        this.fontSize = fontSize;
        this.rgba = new byte[width * height * 4];
        this.uploader = Objects.requireNonNull(uploader, "uploader");
    }

    public synchronized EmojiGlyph require(int codepoint) {
        ensureOpen();
        if (!Character.isValidCodePoint(codepoint)) throw new MissingGlyphException(codepoint);
        EmojiGlyph existing = glyphs.get(codepoint);
        if (existing != null) return existing;
        Raster raster = rasterize(codepoint);
        int x = cursorX, y = cursorY, nextRowHeight = rowHeight;
        if (x + raster.width + 1 > width) { x = 1; y += nextRowHeight + 1; nextRowHeight = 0; }
        if (x + raster.width + 1 > width || y + raster.height + 1 > height) {
            throw new AtlasExhaustedException("Emoji atlas exhausted for U+" + Integer.toHexString(codepoint));
        }
        for (int row = 0; row < raster.height; row++) {
            System.arraycopy(raster.rgba, row * raster.width * 4, rgba, ((y + row) * width + x) * 4,
                    raster.width * 4);
        }
        GlyphAtlasUpload nextUpload;
        try {
            nextUpload = Objects.requireNonNull(uploader.upload(new AtlasPixels(width, height, revision + 1,
                    AtlasPixelFormat.RGBA8, rgba)), "uploader result");
        } catch (RuntimeException error) {
            for (int row = 0; row < raster.height; row++) {
                java.util.Arrays.fill(rgba, ((y + row) * width + x) * 4,
                        ((y + row) * width + x + raster.width) * 4, (byte) 0);
            }
            throw new GlyphUploadException("Failed to upload emoji atlas", error);
        }
        GlyphAtlasUpload previous = upload;
        upload = nextUpload;
        revision++;
        GlyphUv uv = new GlyphUv(x / (float) width, y / (float) height,
                (x + raster.width) / (float) width, (y + raster.height) / (float) height);
        EmojiGlyph glyph = new EmojiGlyph(codepoint, raster.width, raster.height, this, uv);
        glyphs.put(codepoint, glyph);
        cursorX = x + raster.width + 1;
        cursorY = y;
        rowHeight = Math.max(nextRowHeight, raster.height);
        if (previous != null) previous.close();
        return glyph;
    }

    public synchronized GlyphAtlasUpload upload() { ensureOpen(); return upload; }
    public synchronized long revision() { ensureOpen(); return revision; }

    private Raster rasterize(int codepoint) {
        String text = new String(Character.toChars(codepoint));
        Font font = new Font(systemEmojiFamily(), Font.PLAIN, fontSize);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D probeGraphics = probe.createGraphics();
        configure(probeGraphics);
        probeGraphics.setFont(font);
        FontMetrics metrics = probeGraphics.getFontMetrics();
        int glyphWidth = Math.max(1, metrics.stringWidth(text) + 4);
        int glyphHeight = Math.max(1, metrics.getHeight() + 4);
        int baseline = 2 + metrics.getAscent();
        probeGraphics.dispose();

        BufferedImage image = new BufferedImage(glyphWidth, glyphHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        configure(graphics);
        graphics.setFont(font);
        graphics.setColor(Color.WHITE);
        graphics.drawString(text, 2, baseline);
        graphics.dispose();
        byte[] pixels = new byte[glyphWidth * glyphHeight * 4];
        for (int y = 0; y < glyphHeight; y++) for (int x = 0; x < glyphWidth; x++) {
            int argb = image.getRGB(x, y);
            int base = (y * glyphWidth + x) * 4;
            pixels[base] = (byte) (argb >>> 16);
            pixels[base + 1] = (byte) (argb >>> 8);
            pixels[base + 2] = (byte) argb;
            pixels[base + 3] = (byte) (argb >>> 24);
        }
        return new Raster(glyphWidth, glyphHeight, pixels);
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }
    private static String systemEmojiFamily() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "Segoe UI Emoji";
        if (os.contains("mac")) return "Apple Color Emoji";
        return "Noto Color Emoji";
    }
    private void ensureOpen() { if (closed) throw new FontClosedException("Emoji atlas is closed"); }
    @Override public synchronized void close() {
        if (!closed) { closed = true; glyphs.clear(); if (upload != null) upload.close(); upload = null; }
    }
    private record Raster(int width, int height, byte[] rgba) {}
}
