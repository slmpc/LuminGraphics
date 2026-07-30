package com.github.slmpc.lumingraphics.render.renderer;
import com.github.slmpc.lumingraphics.render.RenderResources;
import com.github.slmpc.lumingraphics.render.immediate.VertexBatch;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
public final class ShadowRenderer extends AbstractRenderer<Render2DCommand.Shadow> {
    public ShadowRenderer(RenderResources r, int c) { super(Render2DCommand.Shadow.class, r, c, "shadow"); }
    @Override protected VertexBatch vertices(Render2DCommand.Shadow c) { return VertexBatches.round(c.bounds(), c.radius(), c.color()); }
}
