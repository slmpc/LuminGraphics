package com.github.slmpc.lumingraphics.text.atlas;

@FunctionalInterface
public interface GlyphAtlasUploader {
    GlyphAtlasUpload upload(AtlasPixels pixels);
}
