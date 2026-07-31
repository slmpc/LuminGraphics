package com.github.slmpc.lumingraphics.text.atlas;

/** Retained reference to one uploaded glyph-atlas revision. */
public interface GlyphAtlasUploadLease extends AutoCloseable {
    GlyphAtlasUpload upload();

    GlyphAtlasUploadLease retain();

    @Override
    void close();
}
