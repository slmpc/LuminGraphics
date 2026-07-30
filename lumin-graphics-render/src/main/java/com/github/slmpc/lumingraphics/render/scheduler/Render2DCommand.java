package com.github.slmpc.lumingraphics.render.scheduler;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import java.util.List;

public sealed interface Render2DCommand permits Render2DCommand.Shadow, Render2DCommand.RoundRect,
        Render2DCommand.RoundRectOutline, Render2DCommand.Rect, Render2DCommand.Triangle,
        Render2DCommand.Texture, Render2DCommand.Glyphs {
    int layer();
    long sequence();
    Render2DBounds bounds();
    Render2DScissor scissor();
    Render2DCommandKind kind();

    record Shadow(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                  float radius, float blur, LuminColor color) implements Render2DCommand {
        public Shadow { require(bounds, color); nonNegative("radius", radius); nonNegative("blur", blur); }
        @Override public Render2DCommandKind kind() { return Render2DCommandKind.SHADOW; }
        @Override public Render2DBounds bounds() { return bounds.expand(blur); }
    }
    record RoundRect(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                     float radius, LuminColor color) implements Render2DCommand {
        public RoundRect { require(bounds, color); nonNegative("radius", radius); }
        @Override public Render2DCommandKind kind() { return Render2DCommandKind.ROUND_RECT; }
    }
    record RoundRectOutline(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                            float radius, float width, LuminColor color) implements Render2DCommand {
        public RoundRectOutline { require(bounds, color); nonNegative("radius", radius); nonNegative("outline width", width); }
        @Override public Render2DCommandKind kind() { return Render2DCommandKind.ROUND_RECT_OUTLINE; }
    }
    record Rect(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                LuminColor color) implements Render2DCommand {
        public Rect { require(bounds, color); }
        @Override public Render2DCommandKind kind() { return Render2DCommandKind.RECT; }
    }
    record Triangle(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                    float x1, float y1, float x2, float y2, float x3, float y3,
                    LuminColor color) implements Render2DCommand {
        public Triangle { require(bounds, color); }
        @Override public Render2DCommandKind kind() { return Render2DCommandKind.TRIANGLE; }
    }
    record Texture(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                   Render2DTexture texture, LuminColor color) implements Render2DCommand {
        public Texture { require(bounds, color); if (texture == null) throw new IllegalArgumentException("texture is null"); }
        @Override public Render2DCommandKind kind() { return Render2DCommandKind.TEXTURE; }
    }
    record Glyphs(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                  Render2DTexture texture, List<GlyphQuad> glyphs) implements Render2DCommand {
        public Glyphs {
            if (bounds == null || texture == null || glyphs == null || glyphs.isEmpty() || glyphs.stream().anyMatch(java.util.Objects::isNull))
                throw new IllegalArgumentException("glyph batch is invalid");
            glyphs = List.copyOf(glyphs);
        }
        @Override public Render2DCommandKind kind() { return Render2DCommandKind.GLYPH; }
    }

    private static void require(Render2DBounds bounds, LuminColor color) {
        if (bounds == null || color == null) throw new IllegalArgumentException("render command data is null");
    }
    private static void nonNegative(String name, float value) {
        if (!Float.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
}
