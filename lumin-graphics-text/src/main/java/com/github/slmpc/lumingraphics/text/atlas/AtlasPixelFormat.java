package com.github.slmpc.lumingraphics.text.atlas;

public enum AtlasPixelFormat {
    ALPHA8(1),
    RGBA8(4);

    private final int bytesPerPixel;

    AtlasPixelFormat(int bytesPerPixel) {
        this.bytesPerPixel = bytesPerPixel;
    }

    public int bytesPerPixel() {
        return bytesPerPixel;
    }
}
