package com.github.slmpc.lumingraphics.core.geometry;

import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;

final class GeometryValidation {
    private GeometryValidation() {
    }

    static double finite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new LuminValidationException(name + " must be finite");
        }
        return value;
    }

    static double nonNegative(String name, double value) {
        finite(name, value);
        if (value < 0.0) {
            throw new LuminValidationException(name + " must not be negative");
        }
        return value;
    }

    static void finiteSum(String name, double first, double second) {
        finite(name, first + second);
    }
}
