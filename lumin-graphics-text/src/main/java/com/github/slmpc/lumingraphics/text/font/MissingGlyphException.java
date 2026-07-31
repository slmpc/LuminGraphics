package com.github.slmpc.lumingraphics.text.font;

public final class MissingGlyphException extends FontException {
    private static final long serialVersionUID = 1L;
    public MissingGlyphException(int codepoint) { super("Font has no glyph for U+" + Integer.toHexString(codepoint).toUpperCase()); }
}
