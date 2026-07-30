package com.github.slmpc.lumingraphics.core.geometry;

public record LogicalSize(double width, double height) {
    public LogicalSize {
        GeometryValidation.nonNegative("width", width);
        GeometryValidation.nonNegative("height", height);
    }
}
