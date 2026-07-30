package com.github.slmpc.lumingraphics.render.renderer;
import com.github.slmpc.lumingraphics.render.RenderResources;
import com.github.slmpc.lumingraphics.render.immediate.VertexBatch;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
public final class TriangleRenderer extends AbstractRenderer<Render2DCommand.Triangle> {
    public TriangleRenderer(RenderResources r, int c) { super(Render2DCommand.Triangle.class, r, c, "triangle"); }
    @Override protected VertexBatch vertices(Render2DCommand.Triangle c) {
        return VertexBatches.triangle(c.centerX(), c.centerY(), c.size(), c.progress(), c.color());
    }
}
