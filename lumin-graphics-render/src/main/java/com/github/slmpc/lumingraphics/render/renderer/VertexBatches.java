package com.github.slmpc.lumingraphics.render.renderer;

import com.github.slmpc.lumingraphics.core.buffer.BufferWriter;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.lumingraphics.render.immediate.VertexBatch;
import com.github.slmpc.lumingraphics.render.scheduler.GlyphQuad;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DBounds;

import java.util.List;

final class VertexBatches {
    private static final int[] CORNERS = {0, 1, 2, 0, 2, 3};
    private VertexBatches() { }

    static VertexBatch positionColor(Render2DBounds b, LuminColor color) {
        BufferWriter out = new BufferWriter(6 * 16);
        corners(b, (x, y, corner) -> positionColor(out, x, y, color));
        return new VertexBatch(out.bytes(), 6, 16);
    }

    static VertexBatch triangle(float x1, float y1, float x2, float y2, float x3, float y3, LuminColor color) {
        BufferWriter out = new BufferWriter(3 * 16);
        positionColor(out, x1, y1, color); positionColor(out, x2, y2, color); positionColor(out, x3, y3, color);
        return new VertexBatch(out.bytes(), 3, 16);
    }

    static VertexBatch round(Render2DBounds b, float radius, LuminColor color) {
        BufferWriter out = new BufferWriter(6 * 48);
        corners(b, (x, y, corner) -> {
            positionColor(out, x, y, color);
            out.putFloat(b.x()).putFloat(b.y()).putFloat(b.right()).putFloat(b.bottom());
            for (int i = 0; i < 4; i++) out.putFloat(radius);
        });
        return new VertexBatch(out.bytes(), 6, 48);
    }

    static VertexBatch outline(Render2DBounds b, float radius, float width, LuminColor color) {
        BufferWriter out = new BufferWriter(6 * 52);
        corners(b, (x, y, corner) -> {
            positionColor(out, x, y, color);
            out.putFloat(b.x()).putFloat(b.y()).putFloat(b.right()).putFloat(b.bottom());
            for (int i = 0; i < 4; i++) out.putFloat(radius);
            out.putFloat(width);
        });
        return new VertexBatch(out.bytes(), 6, 52);
    }

    static VertexBatch texture(Render2DBounds b, LuminColor color) {
        return textured(List.of(new GlyphQuad(b, 0, 0, 1, 1, color)));
    }

    static VertexBatch textured(List<GlyphQuad> quads) {
        BufferWriter out = new BufferWriter(quads.size() * 6 * 56);
        for (GlyphQuad quad : quads) {
            Render2DBounds b = quad.bounds();
            corners(b, (x, y, corner) -> {
                positionColor(out, x, y, quad.color());
                float u = corner == 0 || corner == 1 ? quad.u0() : quad.u1();
                float v = corner == 0 || corner == 3 ? quad.v0() : quad.v1();
                out.putFloat(u).putFloat(v);
                out.putFloat(b.x()).putFloat(b.y()).putFloat(b.right()).putFloat(b.bottom());
                for (int i = 0; i < 4; i++) out.putFloat(0);
            });
        }
        return new VertexBatch(out.bytes(), quads.size() * 6, 56);
    }

    private static void positionColor(BufferWriter out, float x, float y, LuminColor color) {
        out.putFloat(x).putFloat(y).putFloat(0).putInt(color.toRgba8());
    }

    private static void corners(Render2DBounds b, CornerWriter writer) {
        float[] x = {b.x(), b.x(), b.right(), b.right()};
        float[] y = {b.y(), b.bottom(), b.bottom(), b.y()};
        for (int corner : CORNERS) writer.write(x[corner], y[corner], corner);
    }

    @FunctionalInterface private interface CornerWriter { void write(float x, float y, int corner); }
}
