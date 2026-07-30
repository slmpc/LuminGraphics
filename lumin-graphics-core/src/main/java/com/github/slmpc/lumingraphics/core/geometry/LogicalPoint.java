package com.github.slmpc.lumingraphics.core.geometry;

public record LogicalPoint(double x, double y) {
    public LogicalPoint {
        GeometryValidation.finite("x", x);
        GeometryValidation.finite("y", y);
    }
}
