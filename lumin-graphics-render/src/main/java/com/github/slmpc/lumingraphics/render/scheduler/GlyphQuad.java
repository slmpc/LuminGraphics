package com.github.slmpc.lumingraphics.render.scheduler;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

public record GlyphQuad(Render2DBounds bounds, float u0, float v0, float u1, float v1, LuminColor color) {
    public GlyphQuad {
        if (bounds == null || color == null || !unit(u0) || !unit(v0) || !unit(u1) || !unit(v1)) {
            throw new IllegalArgumentException("glyph data is invalid");
        }
    }

    private static boolean unit(float value) {
        return Float.isFinite(value) && value >= 0 && value <= 1;
    }
}
