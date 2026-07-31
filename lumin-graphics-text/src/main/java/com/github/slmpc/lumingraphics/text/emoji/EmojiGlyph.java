package com.github.slmpc.lumingraphics.text.emoji;
import com.github.slmpc.lumingraphics.text.atlas.GlyphUv;

public record EmojiGlyph(int codepoint, int width, int height, SystemEmojiAtlas atlas, GlyphUv uv) {}
