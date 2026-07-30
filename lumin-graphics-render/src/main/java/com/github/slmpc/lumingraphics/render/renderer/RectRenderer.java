package com.github.slmpc.lumingraphics.render.renderer;
import com.github.slmpc.lumingraphics.render.RenderResources;
import com.github.slmpc.lumingraphics.render.immediate.VertexBatch;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
public final class RectRenderer extends AbstractRenderer<Render2DCommand.Rect> {
    public RectRenderer(RenderResources r, int c) { super(Render2DCommand.Rect.class, r, c, "rectangle"); }
    @Override protected VertexBatch vertices(Render2DCommand.Rect c) {
        return VertexBatches.positionColors(c.bounds(), c.topLeft(), c.bottomLeft(), c.bottomRight(), c.topRight());
    }
}
