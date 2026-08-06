package com.github.slmpc.lumingraphics.text.font;

import com.github.slmpc.lumingraphics.text.atlas.GlyphDescriptor;

import java.util.concurrent.CompletableFuture;

/**
 * MIG-TEXT-FONT-LOADER
 */
public interface FontLoader extends AutoCloseable {
    CompletableFuture<GlyphDescriptor> requestGlyph(int codepoint);

    GlyphDescriptor requireGlyph(int codepoint);

    int advance(int codepoint);

    int kerning(int left, int right);

    FontMetrics metrics();

    long glyphRevision();

    long atlasRevision();

    @Override
    void close();
}
