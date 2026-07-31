package com.github.slmpc.lumingraphics.core.context;

import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;
import com.github.slmpc.prismrhi.command.RhiCommandBuffer;
import com.github.slmpc.prismrhi.device.RhiDevice;

/** Immutable, injected state for one render submission. */
public record RenderContext(
        RhiDevice device,
        RhiCommandBuffer commands,
        long frameId,
        long completedFrameId,
        int framebufferWidth,
        int framebufferHeight
) {
    public RenderContext {
        if (device == null || commands == null) {
            throw new LuminValidationException("render context device and commands must not be null");
        }
        if (frameId < 0 || completedFrameId >= frameId) {
            throw new LuminValidationException("render frame ids are invalid");
        }
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            throw new LuminValidationException("framebuffer dimensions must be positive");
        }
    }
}
