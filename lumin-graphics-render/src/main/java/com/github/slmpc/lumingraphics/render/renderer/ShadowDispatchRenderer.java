package com.github.slmpc.lumingraphics.render.renderer;

import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;

import java.util.List;

final class ShadowDispatchRenderer implements Renderer {
    private final ShadowRenderer normal;
    private final SegmentedShadowRenderer segmented;

    ShadowDispatchRenderer(RenderResources resources, int capacity) {
        normal = new ShadowRenderer(resources, capacity);
        segmented = new SegmentedShadowRenderer(resources, capacity);
    }

    @Override
    public void beginFrame(RenderExecution execution) {
        normal.beginFrame(execution);
        try {
            segmented.beginFrame(execution);
        } catch (RuntimeException failure) {
            normal.endFrame();
            throw failure;
        }
    }

    @Override
    public void render(Render2DCommand command, RenderExecution execution) {
        target(command).render(command, execution);
    }

    @Override
    public void renderBatch(List<Render2DCommand> commands, RenderExecution execution) {
        if (commands == null || commands.isEmpty()) throw new IllegalArgumentException("render batch is empty");
        Renderer target = target(commands.get(0));
        if (commands.stream().anyMatch(command -> target(command) != target)) {
            throw new IllegalArgumentException("normal and segmented shadows cannot share a batch");
        }
        target.renderBatch(commands, execution);
    }

    private Renderer target(Render2DCommand command) {
        if (command instanceof Render2DCommand.Shadow) return normal;
        if (command instanceof Render2DCommand.SegmentedShadow) return segmented;
        throw new IllegalArgumentException("shadow renderer command kind mismatch");
    }

    @Override
    public void endFrame() {
        if (segmented.frameActive()) segmented.endFrame();
        if (normal.frameActive()) normal.endFrame();
    }

    @Override
    public boolean frameActive() {
        return normal.frameActive() || segmented.frameActive();
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        try {
            segmented.close();
        } catch (RuntimeException closeFailure) {
            failure = closeFailure;
        }
        try {
            normal.close();
        } catch (RuntimeException closeFailure) {
            if (failure == null) failure = closeFailure;
            else failure.addSuppressed(closeFailure);
        }
        if (failure != null) throw failure;
    }
}
