package com.github.slmpc.lumingraphics.render.renderer;

import com.github.slmpc.lumingraphics.render.RenderExecution;
import com.github.slmpc.lumingraphics.render.RenderResources;
import com.github.slmpc.lumingraphics.render.immediate.LuminImmediateRenderer;
import com.github.slmpc.lumingraphics.render.immediate.VertexBatch;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSet;
import java.util.List;

abstract class AbstractRenderer<C extends Render2DCommand> implements Renderer {
    private final Class<C> commandType;
    private final LuminImmediateRenderer immediate;
    private final String pipeline;

    AbstractRenderer(Class<C> commandType, RenderResources resources, int capacity, String pipeline) {
        this.commandType = commandType;
        this.immediate = new LuminImmediateRenderer(resources, capacity);
        this.pipeline = pipeline;
    }

    @Override public final void beginFrame(RenderExecution execution) { immediate.beginFrame(execution); }
    @Override public final void render(Render2DCommand command, RenderExecution execution) {
        renderBatch(List.of(command), execution);
    }
    @Override public final void renderBatch(List<Render2DCommand> commands, RenderExecution execution) {
        if (commands == null || commands.isEmpty() || commands.stream().anyMatch(command -> !commandType.isInstance(command)))
            throw new IllegalArgumentException("renderer command kind mismatch");
        List<C> typed = commands.stream().map(commandType::cast).toList();
        C first = typed.get(0);
        RhiDescriptorSet descriptor = descriptor(first, execution);
        VertexBatch vertices = VertexBatch.combine(typed.stream().map(this::vertices).toList());
        if (descriptor == null) {
            immediate.draw(vertices, pipeline, texture(first), execution);
        } else {
            immediate.drawWithDescriptor(vertices, pipeline, descriptor, execution);
        }
    }
    protected abstract VertexBatch vertices(C command);
    protected Render2DTexture texture(C command) { return null; }
    protected RhiDescriptorSet descriptor(C command, RenderExecution execution) { return null; }
    @Override public final void endFrame() { immediate.endFrame(); }
    @Override public final boolean frameActive() { return immediate.frameActive(); }
    @Override public final void close() { immediate.close(); }
}
