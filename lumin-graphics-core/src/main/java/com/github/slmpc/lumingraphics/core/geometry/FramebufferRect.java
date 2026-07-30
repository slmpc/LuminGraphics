package com.github.slmpc.lumingraphics.core.geometry;

import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;
import com.github.slmpc.prismrhi.rendering.RhiRect2D;

public record FramebufferRect(int x, int y, int width, int height) {
    public FramebufferRect {
        if (x < 0 || y < 0 || width < 0 || height < 0) {
            throw new LuminValidationException("framebuffer rectangle values must not be negative");
        }
        if ((long) x + width > Integer.MAX_VALUE || (long) y + height > Integer.MAX_VALUE) {
            throw new LuminValidationException("framebuffer rectangle edge overflow");
        }
    }

    public RhiRect2D toRhi() {
        if (!isVisible()) {
            throw new LuminValidationException("an empty scissor cannot be submitted to the RHI");
        }
        return RhiRect2D.of(x, y, width, height);
    }

    public boolean isVisible() {
        return width > 0 && height > 0;
    }
}
