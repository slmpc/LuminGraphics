package com.github.slmpc.lumingraphics.render.frame;

import com.github.slmpc.lumingraphics.core.context.RenderContext;
import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.prismrhi.command.RhiCommandBuffer;

/** Render-module services paired with the core per-frame context. */
public record RenderExecution(RenderContext context, RenderResources resources) {
    public RenderExecution {
        if (context == null || resources == null || context.device() != resources.device()) {
            throw new IllegalArgumentException("render execution dependencies must share one device");
        }
    }

    public RenderExecution(RhiCommandBuffer commands, RenderResources resources, long frameId,
                           long completedFrameId, int width, int height) {
        this(new RenderContext(resources.device(), commands, frameId, completedFrameId, width, height), resources);
    }

    public RhiCommandBuffer commands() { return context.commands(); }
    public long frameId() { return context.frameId(); }
    public long completedFrameId() { return context.completedFrameId(); }
    public int width() { return context.framebufferWidth(); }
    public int height() { return context.framebufferHeight(); }
}
