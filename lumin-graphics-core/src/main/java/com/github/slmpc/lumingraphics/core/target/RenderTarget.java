package com.github.slmpc.lumingraphics.core.target;

import com.github.slmpc.lumingraphics.core.exception.LuminContextMismatchException;
import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;
import com.github.slmpc.lumingraphics.core.geometry.FramebufferRect;
import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.resource.RhiImageView;

import java.util.Optional;

public record RenderTarget(
        RhiImageView colorView,
        Optional<RhiImageView> depthView,
        int width,
        int height,
        RhiContextIdentity contextIdentity
) {
    public RenderTarget {
        if (colorView == null || depthView == null || contextIdentity == null) {
            throw new LuminValidationException("render target values must not be null");
        }
        new FramebufferRect(0, 0, width, height);
        if (width == 0 || height == 0) {
            throw new LuminValidationException("render target dimensions must be positive");
        }
    }

    public void requireContext(RhiContextIdentity expected) {
        try {
            contextIdentity.requireSameContext(expected);
        } catch (RuntimeException mismatch) {
            throw new LuminContextMismatchException("render target belongs to another RHI context", mismatch);
        }
    }
}
