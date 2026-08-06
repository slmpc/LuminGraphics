package com.github.slmpc.lumingraphics.text.layout;

import com.github.slmpc.lumingraphics.text.atlas.GlyphUv;

public record GlyphPlacement(int codepoint, float x0, float y0, float x1, float y1, GlyphUv uv) {
}
