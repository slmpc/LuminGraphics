package com.github.slmpc.lumingraphics.core.geometry;

import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;

public record LuminColor(float red, float green, float blue, float alpha) {
    public LuminColor {
        component("red", red);
        component("green", green);
        component("blue", blue);
        component("alpha", alpha);
    }

    public static LuminColor fromArgb(int argb) {
        return new LuminColor(
                ((argb >>> 16) & 0xff) / 255.0f,
                ((argb >>> 8) & 0xff) / 255.0f,
                (argb & 0xff) / 255.0f,
                ((argb >>> 24) & 0xff) / 255.0f
        );
    }

    public int toRgba8() {
        return byteValue(red) << 24 | byteValue(green) << 16 | byteValue(blue) << 8 | byteValue(alpha);
    }

    private static void component(String name, float value) {
        if (!Float.isFinite(value) || value < 0.0f || value > 1.0f) {
            throw new LuminValidationException(name + " must be finite and between zero and one");
        }
    }

    private static int byteValue(float value) {
        return Math.round(value * 255.0f);
    }
}
