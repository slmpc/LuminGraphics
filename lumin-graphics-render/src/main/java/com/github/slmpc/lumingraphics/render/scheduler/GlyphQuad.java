package com.github.slmpc.lumingraphics.render.scheduler;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

public record GlyphQuad(Render2DBounds bounds, float u0, float v0, float u1, float v1, LuminColor color) {
    public GlyphQuad {
        if (bounds == null || color == null || !Float.isFinite(u0) || !Float.isFinite(v0)
                || !Float.isFinite(u1) || !Float.isFinite(v1)) throw new IllegalArgumentException("glyph data is invalid");
    }
}
