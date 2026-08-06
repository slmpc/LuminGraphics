package com.github.slmpc.lumingraphics.text.atlas;

/**
 * MIG-TEXT-GLYPH-DESCRIPTOR
 */
public record GlyphDescriptor(int codepoint, TtfGlyphAtlas atlas, GlyphUv uv, int width, int height,
                              int xOffset, int yOffset, int advance) {
}
