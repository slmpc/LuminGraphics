package com.github.slmpc.lumingraphics.render.immediate;

import com.github.slmpc.lumingraphics.core.buffer.BufferWriter;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

/**
 * CPU tessellator for the shared position/color vertex ABI.
 */
public final class LuminTessellator {
    public static final int POSITION_COLOR_STRIDE = 16;
    private final BufferWriter writer;
    private int vertices;

    public LuminTessellator(int maxVertices) {
        writer = new BufferWriter(Math.multiplyExact(maxVertices, POSITION_COLOR_STRIDE));
    }

    public LuminTessellator vertex(float x, float y, float z, LuminColor color) {
        if (color == null) throw new IllegalArgumentException("vertex color must not be null");
        writer.putFloat(x).putFloat(y).putFloat(z).putRgba8(color.toRgba8());
        vertices++;
        return this;
    }

    public LuminTessellator quad(float x, float y, float width, float height, LuminColor color) {
        float right = x + width;
        float bottom = y + height;
        return vertex(x, y, 0, color).vertex(x, bottom, 0, color).vertex(right, bottom, 0, color)
                .vertex(x, y, 0, color).vertex(right, bottom, 0, color).vertex(right, y, 0, color);
    }

    public VertexBatch build() {
        if (vertices == 0) throw new IllegalStateException("cannot build an empty vertex batch");
        return new VertexBatch(writer.bytes(), vertices, POSITION_COLOR_STRIDE);
    }
}
