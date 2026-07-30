package com.github.slmpc.lumingraphics.core.geometry;

import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;

public final class CoordinateConverter {
    private CoordinateConverter() {
    }

    public static FramebufferRect toFramebufferScissor(
            LogicalRect logical,
            SurfaceMetrics metrics,
            CoordinateOrigin origin,
            ScissorPolicy policy
    ) {
        if (logical == null || metrics == null || origin == null || policy == null) {
            throw new LuminValidationException("coordinate conversion values must not be null");
        }
        long left = floorToLong(logical.x() * metrics.scale());
        long right = ceilToLong(logical.right() * metrics.scale());
        long firstY = floorToLong(logical.y() * metrics.scale());
        long secondY = ceilToLong(logical.bottom() * metrics.scale());
        long bottom = origin == CoordinateOrigin.TOP_LEFT
                ? (long) metrics.framebufferHeight() - secondY
                : firstY;
        long top = origin == CoordinateOrigin.TOP_LEFT
                ? (long) metrics.framebufferHeight() - firstY
                : secondY;
        return applyBounds(left, bottom, right, top, metrics, policy);
    }

    private static FramebufferRect applyBounds(
            long left, long bottom, long right, long top,
            SurfaceMetrics metrics, ScissorPolicy policy
    ) {
        long width = metrics.framebufferWidth();
        long height = metrics.framebufferHeight();
        boolean outside = left < 0 || bottom < 0 || right > width || top > height;
        if (outside && policy == ScissorPolicy.REJECT_OUT_OF_BOUNDS) {
            throw new LuminValidationException("scissor lies outside the framebuffer");
        }
        long boundedLeft = Math.max(0, Math.min(width, left));
        long boundedBottom = Math.max(0, Math.min(height, bottom));
        long boundedRight = Math.max(boundedLeft, Math.min(width, right));
        long boundedTop = Math.max(boundedBottom, Math.min(height, top));
        return new FramebufferRect(
                toInt(boundedLeft), toInt(boundedBottom),
                toInt(boundedRight - boundedLeft), toInt(boundedTop - boundedBottom)
        );
    }

    private static long floorToLong(double value) {
        GeometryValidation.finite("scaled coordinate", value);
        double rounded = Math.floor(snapToPixelBoundary(value));
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new LuminValidationException("scaled coordinate overflows 32-bit framebuffer coordinates");
        }
        return (long) rounded;
    }

    private static long ceilToLong(double value) {
        GeometryValidation.finite("scaled coordinate", value);
        double rounded = Math.ceil(snapToPixelBoundary(value));
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new LuminValidationException("scaled coordinate overflows 32-bit framebuffer coordinates");
        }
        return (long) rounded;
    }

    private static double snapToPixelBoundary(double value) {
        double nearestPixel = Math.rint(value);
        double tolerance = Math.max(Math.ulp(value), Math.ulp(nearestPixel));
        return Math.abs(value - nearestPixel) <= tolerance ? nearestPixel : value;
    }

    private static int toInt(long value) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new LuminValidationException("framebuffer coordinate overflow");
        }
        return (int) value;
    }
}
