package com.github.slmpc.lumingraphics.render.renderer;

import com.github.slmpc.lumingraphics.core.buffer.BufferWriter;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.lumingraphics.render.immediate.VertexBatch;
import com.github.slmpc.lumingraphics.render.scheduler.GlyphQuad;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DBounds;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;

import java.util.List;

final class VertexBatches {
    private static final int[] CORNERS = {0, 1, 2, 0, 2, 3};
    private VertexBatches() { }

    static VertexBatch positionColors(Render2DBounds bounds, LuminColor topLeft, LuminColor bottomLeft,
                                      LuminColor bottomRight, LuminColor topRight) {
        LuminColor[] colors = {topLeft, bottomLeft, bottomRight, topRight};
        BufferWriter out = new BufferWriter(6 * 16);
        corners(bounds, (x, y, corner) -> positionColor(out, x, y, 0, colors[corner]));
        return new VertexBatch(out.bytes(), 6, 16);
    }

    static VertexBatch triangle(float centerX, float centerY, float size, float progress, LuminColor color) {
        float rightTipX = centerX + size;
        float rightTipY = centerY;
        float rightBase1X = centerX - size;
        float rightBase1Y = centerY - size;
        float rightBase2X = centerX - size;
        float rightBase2Y = centerY + size;
        float downTipX = centerX;
        float downTipY = centerY + size;
        float downBase1X = centerX - size;
        float downBase1Y = centerY - size;
        float downBase2X = centerX + size;
        float downBase2Y = centerY - size;
        BufferWriter out = new BufferWriter(3 * 16);
        positionColor(out, lerp(rightBase1X, downBase1X, progress), lerp(rightBase1Y, downBase1Y, progress), 0, color);
        positionColor(out, lerp(rightBase2X, downBase2X, progress), lerp(rightBase2Y, downBase2Y, progress), 0, color);
        positionColor(out, lerp(rightTipX, downTipX, progress), lerp(rightTipY, downTipY, progress), 0, color);
        return new VertexBatch(out.bytes(), 3, 16);
    }

    static VertexBatch round(Render2DBounds bounds, float topLeftRadius, float topRightRadius,
                             float bottomRightRadius, float bottomLeftRadius,
                             LuminColor topLeft, LuminColor bottomLeft,
                             LuminColor bottomRight, LuminColor topRight) {
        LuminColor[] colors = {topLeft, bottomLeft, bottomRight, topRight};
        BufferWriter out = new BufferWriter(6 * 48);
        corners(bounds, (x, y, corner) -> {
            positionColor(out, x, y, 0, colors[corner]);
            innerRect(out, bounds);
            radii(out, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius);
        });
        return new VertexBatch(out.bytes(), 6, 48);
    }

    static VertexBatch shadow(Render2DBounds shape, float topLeftRadius, float topRightRadius,
                              float bottomRightRadius, float bottomLeftRadius,
                              float blurRadius, LuminColor color) {
        BufferWriter out = new BufferWriter(6 * 48);
        corners(shape.expand(blurRadius), (x, y, corner) -> {
            positionColor(out, x, y, blurRadius, color);
            innerRect(out, shape);
            radii(out, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius);
        });
        return new VertexBatch(out.bytes(), 6, 48);
    }

    static VertexBatch segmentedShadow(Render2DCommand.SegmentedShadow command) {
        float[] rects = command.segmentRects();
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < command.segmentCount(); i++) {
            int offset = i * 4;
            minX = Math.min(minX, rects[offset]);
            minY = Math.min(minY, rects[offset + 1]);
            maxX = Math.max(maxX, rects[offset] + rects[offset + 2]);
            maxY = Math.max(maxY, rects[offset + 1] + rects[offset + 3]);
        }
        Render2DBounds bounds = new Render2DBounds(minX - command.blurRadius(), minY - command.blurRadius(),
                maxX - minX + command.blurRadius() * 2, maxY - minY + command.blurRadius() * 2);
        BufferWriter out = new BufferWriter(6 * 12);
        corners(bounds, (x, y, corner) -> out.putFloat(x).putFloat(y).putFloat(0));
        return new VertexBatch(out.bytes(), 6, 12);
    }

    static VertexBatch outline(Render2DBounds shape, float topLeftRadius, float topRightRadius,
                               float bottomRightRadius, float bottomLeftRadius,
                               float width, LuminColor color) {
        BufferWriter out = new BufferWriter(6 * 52);
        corners(shape.expand(width * 0.5f), (x, y, corner) -> {
            positionColor(out, x, y, 0, color);
            innerRect(out, shape);
            radii(out, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius);
            out.putFloat(width);
        });
        return new VertexBatch(out.bytes(), 6, 52);
    }

    static VertexBatch texture(Render2DCommand.Texture command) {
        Render2DBounds bounds = command.quadBounds();
        float[] transformed = transformedCorners(bounds, command.originX(), command.originY(), command.rotationDegrees());
        Render2DBounds inner = command.rotationDegrees() == 0 ? bounds : command.bounds();
        BufferWriter out = new BufferWriter(6 * 56);
        for (int corner : CORNERS) {
            float u = corner == 0 || corner == 1 ? command.u0() : command.u1();
            float v = corner == 0 || corner == 3 ? command.v0() : command.v1();
            positionColor(out, transformed[corner * 2], transformed[corner * 2 + 1], 0, command.color());
            out.putFloat(u).putFloat(v);
            innerRect(out, inner);
            radii(out, command.radiusTopLeft(), command.radiusTopRight(),
                    command.radiusBottomRight(), command.radiusBottomLeft());
        }
        return new VertexBatch(out.bytes(), 6, 56);
    }

    static VertexBatch glyphs(List<GlyphQuad> quads, float originX, float originY, float rotationDegrees) {
        BufferWriter out = new BufferWriter(quads.size() * 6 * 24);
        for (GlyphQuad quad : quads) {
            float[] transformed = transformedCorners(quad.bounds(), originX, originY, rotationDegrees);
            for (int corner : CORNERS) {
                float u = corner == 0 || corner == 1 ? quad.u0() : quad.u1();
                float v = corner == 0 || corner == 3 ? quad.v0() : quad.v1();
                out.putFloat(transformed[corner * 2]).putFloat(transformed[corner * 2 + 1]).putFloat(0);
                out.putFloat(u).putFloat(v).putRgba8(quad.color().toRgba8());
            }
        }
        return new VertexBatch(out.bytes(), quads.size() * 6, 24);
    }

    private static float[] transformedCorners(Render2DBounds bounds, float originX, float originY,
                                               float rotationDegrees) {
        float[] result = {bounds.x(), bounds.y(), bounds.x(), bounds.bottom(),
                bounds.right(), bounds.bottom(), bounds.right(), bounds.y()};
        if (rotationDegrees == 0) return result;
        double radians = Math.toRadians(rotationDegrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        for (int i = 0; i < result.length; i += 2) {
            float dx = result[i] - originX;
            float dy = result[i + 1] - originY;
            result[i] = originX + dx * cos - dy * sin;
            result[i + 1] = originY + dx * sin + dy * cos;
        }
        return result;
    }

    private static void positionColor(BufferWriter out, float x, float y, float z, LuminColor color) {
        out.putFloat(x).putFloat(y).putFloat(z).putRgba8(color.toRgba8());
    }

    private static void innerRect(BufferWriter out, Render2DBounds bounds) {
        out.putFloat(bounds.x()).putFloat(bounds.y()).putFloat(bounds.right()).putFloat(bounds.bottom());
    }

    private static void radii(BufferWriter out, float topLeft, float topRight,
                              float bottomRight, float bottomLeft) {
        out.putFloat(topLeft).putFloat(topRight).putFloat(bottomRight).putFloat(bottomLeft);
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static void corners(Render2DBounds bounds, CornerWriter writer) {
        float[] x = {bounds.x(), bounds.x(), bounds.right(), bounds.right()};
        float[] y = {bounds.y(), bounds.bottom(), bounds.bottom(), bounds.y()};
        for (int corner : CORNERS) writer.write(x[corner], y[corner], corner);
    }

    @FunctionalInterface private interface CornerWriter { void write(float x, float y, int corner); }
}
