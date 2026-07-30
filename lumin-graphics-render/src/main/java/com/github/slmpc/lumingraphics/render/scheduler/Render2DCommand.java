package com.github.slmpc.lumingraphics.render.scheduler;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import java.util.List;

public sealed interface Render2DCommand permits Render2DCommand.Shadow, Render2DCommand.SegmentedShadow,
        Render2DCommand.RoundRect, Render2DCommand.RoundRectOutline, Render2DCommand.Rect,
        Render2DCommand.Triangle, Render2DCommand.Texture, Render2DCommand.Glyphs {
    int layer();
    long sequence();
    Render2DBounds bounds();
    Render2DScissor scissor();
    Render2DCommandKind kind();

    record Shadow(int layer, long sequence, Render2DBounds shapeBounds, Render2DScissor scissor,
                  float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                  float blurRadius, LuminColor color) implements Render2DCommand {
        public Shadow {
            require(shapeBounds, color);
            radii(radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
            nonNegative("blur radius", blurRadius);
            shapeBounds.expand(blurRadius);
        }
        public Shadow(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                      float radius, float blurRadius, LuminColor color) {
            this(layer, sequence, bounds, scissor, radius, radius, radius, radius, blurRadius, color);
        }
        @Override public Render2DBounds bounds() { return shapeBounds.expand(blurRadius); }
        @Override public Render2DCommandKind kind() { return Render2DCommandKind.SHADOW; }
    }

    record SegmentedShadow(int layer, long sequence, Render2DBounds shapeBounds, Render2DScissor scissor,
                           float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                           float blurRadius, LuminColor color, float[] segmentRects, float[] segmentRadii,
                           int segmentCount) implements Render2DCommand {
        public SegmentedShadow {
            require(shapeBounds, color);
            radii(radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
            nonNegative("blur radius", blurRadius);
            if (segmentCount <= 0 || segmentCount > 64 || segmentRects == null || segmentRadii == null
                    || segmentRects.length < segmentCount * 4 || segmentRadii.length < segmentCount) {
                throw new IllegalArgumentException("segmented shadow arrays do not match segment count 1..64");
            }
            segmentRects = java.util.Arrays.copyOf(segmentRects, segmentCount * 4);
            segmentRadii = java.util.Arrays.copyOf(segmentRadii, segmentCount);
            for (int i = 0; i < segmentCount; i++) {
                int offset = i * 4;
                finite("segment x", segmentRects[offset]);
                finite("segment y", segmentRects[offset + 1]);
                nonNegative("segment width", segmentRects[offset + 2]);
                nonNegative("segment height", segmentRects[offset + 3]);
                nonNegative("segment radius", segmentRadii[i]);
            }
            shapeBounds.expand(blurRadius);
        }
        @Override public float[] segmentRects() { return segmentRects.clone(); }
        @Override public float[] segmentRadii() { return segmentRadii.clone(); }
        @Override public Render2DBounds bounds() { return shapeBounds.expand(blurRadius); }
        @Override public Render2DCommandKind kind() { return Render2DCommandKind.SHADOW; }
    }

    record RoundRect(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                     float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                     LuminColor topLeft, LuminColor bottomLeft, LuminColor bottomRight,
                     LuminColor topRight) implements Render2DCommand {
        public RoundRect {
            require(bounds, topLeft, bottomLeft, bottomRight, topRight);
            radii(radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
        }
        public RoundRect(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                         float radius, LuminColor color) {
            this(layer, sequence, bounds, scissor, radius, radius, radius, radius,
                    color, color, color, color);
        }
        @Override public Render2DCommandKind kind() { return Render2DCommandKind.ROUND_RECT; }
    }

    record RoundRectOutline(int layer, long sequence, Render2DBounds shapeBounds, Render2DScissor scissor,
                            float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                            float width, LuminColor color) implements Render2DCommand {
        public RoundRectOutline {
            require(shapeBounds, color);
            radii(radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
            nonNegative("outline width", width);
            shapeBounds.expand(width * 0.5f);
        }
        public RoundRectOutline(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                                float radius, float width, LuminColor color) {
            this(layer, sequence, bounds, scissor, radius, radius, radius, radius, width, color);
        }
        @Override public Render2DBounds bounds() { return shapeBounds.expand(width * 0.5f); }
        @Override public Render2DCommandKind kind() { return Render2DCommandKind.ROUND_RECT_OUTLINE; }
    }

    record Rect(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                LuminColor topLeft, LuminColor bottomLeft, LuminColor bottomRight,
                LuminColor topRight) implements Render2DCommand {
        public Rect { require(bounds, topLeft, bottomLeft, bottomRight, topRight); }
        public Rect(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                    LuminColor color) {
            this(layer, sequence, bounds, scissor, color, color, color, color);
        }
        @Override public Render2DCommandKind kind() { return Render2DCommandKind.RECT; }
    }

    record Triangle(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                    float centerX, float centerY, float size, float progress,
                    LuminColor color) implements Render2DCommand {
        public Triangle {
            require(bounds, color);
            finite("center x", centerX);
            finite("center y", centerY);
            nonNegative("triangle size", size);
            unit("triangle progress", progress);
        }
        @Override public Render2DCommandKind kind() { return Render2DCommandKind.TRIANGLE; }
    }

    record Texture(int layer, long sequence, Render2DBounds quadBounds, Render2DScissor scissor,
                   Render2DTexture texture, float radiusTopLeft, float radiusTopRight,
                   float radiusBottomRight, float radiusBottomLeft, float u0, float v0, float u1, float v1,
                   LuminColor color, float originX, float originY, float rotationDegrees) implements Render2DCommand {
        public Texture {
            require(quadBounds, color);
            if (texture == null) throw new IllegalArgumentException("texture is null");
            radii(radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
            uv(u0, v0, u1, v1);
            finite("texture origin x", originX);
            finite("texture origin y", originY);
            finite("texture rotation", rotationDegrees);
            if (rotationDegrees != 0 && (radiusTopLeft != 0 || radiusTopRight != 0
                    || radiusBottomRight != 0 || radiusBottomLeft != 0)) {
                throw new IllegalArgumentException("rotated textures cannot carry corner radii");
            }
            if (rotationDegrees != 0) rotatedBounds(quadBounds, originX, originY, rotationDegrees);
        }
        public Texture(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                       Render2DTexture texture, LuminColor color) {
            this(layer, sequence, bounds, scissor, texture, 0, 0, 0, 0,
                    0, 0, 1, 1, color, 0, 0, 0);
        }
        @Override public Render2DBounds bounds() {
            return rotationDegrees == 0 ? quadBounds : rotatedBounds(quadBounds, originX, originY, rotationDegrees);
        }
        @Override public Render2DCommandKind kind() { return Render2DCommandKind.TEXTURE; }
    }

    record Glyphs(int layer, long sequence, Render2DBounds quadBounds, Render2DScissor scissor,
                  Render2DTexture texture, List<GlyphQuad> glyphs,
                  float originX, float originY, float rotationDegrees) implements Render2DCommand {
        public Glyphs {
            if (quadBounds == null || texture == null || glyphs == null || glyphs.isEmpty()
                    || glyphs.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("glyph batch is invalid");
            }
            finite("glyph origin x", originX);
            finite("glyph origin y", originY);
            finite("glyph rotation", rotationDegrees);
            glyphs = List.copyOf(glyphs);
            if (rotationDegrees != 0) rotatedBounds(quadBounds, originX, originY, rotationDegrees);
        }
        public Glyphs(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                      Render2DTexture texture, List<GlyphQuad> glyphs) {
            this(layer, sequence, bounds, scissor, texture, glyphs, 0, 0, 0);
        }
        @Override public Render2DBounds bounds() {
            return rotationDegrees == 0 ? quadBounds : rotatedBounds(quadBounds, originX, originY, rotationDegrees);
        }
        @Override public Render2DCommandKind kind() { return Render2DCommandKind.GLYPH; }
    }

    private static void require(Render2DBounds bounds, LuminColor... colors) {
        if (bounds == null || colors == null || colors.length == 0
                || java.util.Arrays.stream(colors).anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("render command data is null");
        }
    }

    private static void radii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        nonNegative("top-left radius", topLeft);
        nonNegative("top-right radius", topRight);
        nonNegative("bottom-right radius", bottomRight);
        nonNegative("bottom-left radius", bottomLeft);
    }

    private static void uv(float u0, float v0, float u1, float v1) {
        unit("u0", u0); unit("v0", v0); unit("u1", u1); unit("v1", v1);
    }

    private static void unit(String name, float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " must be finite and between zero and one");
        }
    }

    private static void nonNegative(String name, float value) {
        if (!Float.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void finite(String name, float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private static Render2DBounds rotatedBounds(Render2DBounds bounds, float originX, float originY,
                                                 float rotationDegrees) {
        double radians = Math.toRadians(rotationDegrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float[] xs = {bounds.x(), bounds.x(), bounds.right(), bounds.right()};
        float[] ys = {bounds.y(), bounds.bottom(), bounds.bottom(), bounds.y()};
        for (int i = 0; i < 4; i++) {
            float dx = xs[i] - originX;
            float dy = ys[i] - originY;
            float x = originX + dx * cos - dy * sin;
            float y = originY + dx * sin + dy * cos;
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
        }
        return new Render2DBounds(minX, minY, maxX - minX, maxY - minY);
    }
}
