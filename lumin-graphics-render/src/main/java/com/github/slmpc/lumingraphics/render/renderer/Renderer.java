package com.github.slmpc.lumingraphics.render.renderer;

import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;

import java.util.List;

public interface Renderer extends AutoCloseable {
    void beginFrame(RenderExecution execution);

    void render(Render2DCommand command, RenderExecution execution);

    void renderBatch(List<Render2DCommand> commands, RenderExecution execution);

    void endFrame();

    boolean frameActive();

    @Override
    void close();
}
