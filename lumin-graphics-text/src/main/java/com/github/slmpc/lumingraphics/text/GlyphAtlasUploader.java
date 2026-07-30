package com.github.slmpc.lumingraphics.text;

@FunctionalInterface
public interface GlyphAtlasUploader {
    GlyphAtlasUpload upload(AtlasPixels pixels);
}
