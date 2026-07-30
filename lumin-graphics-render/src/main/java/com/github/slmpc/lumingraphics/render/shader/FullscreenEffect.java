package com.github.slmpc.lumingraphics.render.shader;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.lumingraphics.render.RenderExecution;
import com.github.slmpc.lumingraphics.render.RenderResources;
import com.github.slmpc.lumingraphics.render.immediate.LuminImmediateRenderer;
import com.github.slmpc.lumingraphics.render.immediate.LuminTessellator;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;

abstract class FullscreenEffect implements AutoCloseable {
    private final String pipeline;
    private final LuminImmediateRenderer renderer;
    FullscreenEffect(RenderResources resources, int capacity, String pipeline) {
        this.pipeline = pipeline;
        renderer = new LuminImmediateRenderer(resources, capacity);
    }
    final void applyEffect(RenderExecution execution, Render2DTexture input) {
        renderer.beginFrame(execution);
        try {
            var batch = new LuminTessellator(3)
                    .vertex(-1, -1, 0, new LuminColor(1, 1, 1, 1))
                    .vertex(3, -1, 0, new LuminColor(1, 1, 1, 1))
                    .vertex(-1, 3, 0, new LuminColor(1, 1, 1, 1)).build();
            renderer.draw(batch, pipeline, input, execution);
        } finally { renderer.endFrame(); }
    }
    @Override public final void close() { renderer.close(); }
}
