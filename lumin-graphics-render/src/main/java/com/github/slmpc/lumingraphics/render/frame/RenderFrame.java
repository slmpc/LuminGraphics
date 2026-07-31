package com.github.slmpc.lumingraphics.render.frame;

import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.prismrhi.command.RhiCommandBuffer;

/** Owns the begin/end pairing for one caller-supplied Prism command buffer. */
public final class RenderFrame implements AutoCloseable {
    private final RhiCommandBuffer commands;
    private final RenderExecution execution;
    private boolean closed;

    public RenderFrame(RhiCommandBuffer commands, RenderResources resources, long frameId,
                       long completedFrameId, int width, int height) {
        if (commands == null) throw new IllegalArgumentException("commands must not be null");
        this.commands = commands;
        this.execution = new RenderExecution(commands, resources, frameId, completedFrameId, width, height);
        commands.begin();
    }

    public RenderExecution execution() {
        if (closed) throw new IllegalStateException("render frame is closed");
        return execution;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        commands.end();
    }
}
