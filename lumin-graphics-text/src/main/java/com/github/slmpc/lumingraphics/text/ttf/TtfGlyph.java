package com.github.slmpc.lumingraphics.text.ttf;

import java.util.Arrays;

/** MIG-TEXT-TTF-GLYPH */
public record TtfGlyph(int codepoint, int width, int height, int xOffset, int yOffset, int advance, byte[] pixels) {
    public TtfGlyph {
        pixels = Arrays.copyOf(pixels, pixels.length);
        if (width < 0 || height < 0 || pixels.length != width * height) {
            throw new IllegalArgumentException("Invalid glyph bitmap");
        }
    }
    @Override public byte[] pixels() { return Arrays.copyOf(pixels, pixels.length); }
}
