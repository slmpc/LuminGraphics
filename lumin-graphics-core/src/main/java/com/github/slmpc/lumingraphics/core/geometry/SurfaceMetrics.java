package com.github.slmpc.lumingraphics.core.geometry;

import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;

public record SurfaceMetrics(int framebufferWidth, int framebufferHeight, double scale) {
    public SurfaceMetrics {
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            throw new LuminValidationException("framebuffer dimensions must be positive");
        }
        GeometryValidation.finite("scale", scale);
        if (scale <= 0.0) {
            throw new LuminValidationException("scale must be positive");
        }
        if (!Double.isFinite(framebufferWidth / scale) || !Double.isFinite(framebufferHeight / scale)) {
            throw new LuminValidationException("scale produces non-finite logical dimensions");
        }
    }

    public LogicalSize logicalSize() {
        return new LogicalSize(framebufferWidth / scale, framebufferHeight / scale);
    }

    public LogicalRect logicalViewport() {
        LogicalSize size = logicalSize();
        return new LogicalRect(0, 0, size.width(), size.height());
    }

    public FramebufferRect framebufferViewport() {
        return new FramebufferRect(0, 0, framebufferWidth, framebufferHeight);
    }
}
