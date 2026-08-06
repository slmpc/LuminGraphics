package com.github.slmpc.lumingraphics.render.shader;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.lumingraphics.render.immediate.LuminImmediateRenderer;
import com.github.slmpc.lumingraphics.render.immediate.LuminTessellator;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;

import java.nio.ByteBuffer;
import java.util.Objects;

public abstract class FullscreenEffect implements AutoCloseable {
    private final String pipeline;
    private final LuminImmediateRenderer renderer;

    protected FullscreenEffect(RenderResources resources, int capacity, String pipeline) {
        this.pipeline = pipeline;
        renderer = new LuminImmediateRenderer(resources, capacity);
    }

    protected final void applyEffect(RenderExecution execution, Render2DTexture input, ByteBuffer uniforms) {
        Objects.requireNonNull(execution, "execution");
        FullscreenEffectRequest request = new FullscreenEffectRequest(pipeline, input, uniforms);
        FullscreenEffectBinding binding = execution.resources().requireFullscreenEffectBinding(request, execution);
        renderer.beginFrame(execution);
        boolean passStarted = false;
        try {
            binding.pass().begin(execution.commands());
            passStarted = true;
            var batch = new LuminTessellator(3)
                    .vertex(-1, -1, 0, new LuminColor(1, 1, 1, 1))
                    .vertex(3, -1, 0, new LuminColor(1, 1, 1, 1))
                    .vertex(-1, 3, 0, new LuminColor(1, 1, 1, 1)).build();
            renderer.drawWithDescriptor(batch, pipeline, binding.descriptor(), execution);
        } finally {
            try {
                if (passStarted) binding.pass().end(execution.commands());
            } finally {
                renderer.endFrame();
            }
        }
    }

    @Override
    public final void close() {
        renderer.close();
    }
}
