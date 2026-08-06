package com.github.slmpc.lumingraphics.text.atlas;

import java.util.Arrays;
import java.util.Objects;

public record AtlasPixels(int width, int height, long revision, AtlasPixelFormat format, byte[] data) {
    public AtlasPixels {
        Objects.requireNonNull(format, "format");
        if (width <= 0 || height <= 0 || revision <= 0
                || data.length != width * height * format.bytesPerPixel()) {
            throw new IllegalArgumentException("Invalid atlas pixel payload");
        }
        data = Arrays.copyOf(data, data.length);
    }

    public AtlasPixels(int width, int height, long revision, byte[] alpha8) {
        this(width, height, revision, AtlasPixelFormat.ALPHA8, alpha8);
    }

    @Override
    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    public byte[] alpha8() {
        if (format != AtlasPixelFormat.ALPHA8) throw new IllegalStateException("Atlas is not ALPHA8");
        return data();
    }
}
