package com.github.slmpc.lumingraphics.core.geometry;

import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;

import java.util.Optional;

public record LogicalRect(double x, double y, double width, double height) {
    public LogicalRect {
        GeometryValidation.finite("x", x);
        GeometryValidation.finite("y", y);
        GeometryValidation.nonNegative("width", width);
        GeometryValidation.nonNegative("height", height);
        GeometryValidation.finiteSum("right edge", x, width);
        GeometryValidation.finiteSum("bottom edge", y, height);
    }

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public boolean contains(LogicalPoint point) {
        if (point == null) {
            throw new LuminValidationException("point must not be null");
        }
        return point.x() >= x && point.x() <= right() && point.y() >= y && point.y() <= bottom();
    }

    public Optional<LogicalRect> intersection(LogicalRect other) {
        if (other == null) {
            throw new LuminValidationException("intersection rectangle must not be null");
        }
        double left = Math.max(x, other.x);
        double top = Math.max(y, other.y);
        double right = Math.min(right(), other.right());
        double bottom = Math.min(bottom(), other.bottom());
        if (right <= left || bottom <= top) {
            return Optional.empty();
        }
        return Optional.of(new LogicalRect(left, top, right - left, bottom - top));
    }
}
