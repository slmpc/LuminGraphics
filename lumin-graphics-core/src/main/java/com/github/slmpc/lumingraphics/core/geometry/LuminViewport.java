package com.github.slmpc.lumingraphics.core.geometry;

import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;
import com.github.slmpc.prismrhi.rendering.RhiViewport;

public record LuminViewport(LogicalRect bounds, float minDepth, float maxDepth) {
    public LuminViewport {
        if (bounds == null) {
            throw new LuminValidationException("viewport bounds must not be null");
        }
        if (!Float.isFinite(minDepth) || !Float.isFinite(maxDepth)
                || minDepth < 0.0f || maxDepth > 1.0f || minDepth > maxDepth) {
            throw new LuminValidationException("viewport depth must be ordered within [0, 1]");
        }
    }

    public RhiViewport toRhi(SurfaceMetrics metrics, CoordinateOrigin origin) {
        FramebufferRect pixels = CoordinateConverter.toFramebufferScissor(
                bounds, metrics, origin, ScissorPolicy.REJECT_OUT_OF_BOUNDS
        );
        return new RhiViewport(pixels.x(), pixels.y(), pixels.width(), pixels.height(), minDepth, maxDepth);
    }
}
