package com.github.slmpc.lumingraphics.render.renderer;
import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.lumingraphics.render.immediate.VertexBatch;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
public final class RoundRectRenderer extends AbstractRenderer<Render2DCommand.RoundRect> {
    public RoundRectRenderer(RenderResources r, int c) { super(Render2DCommand.RoundRect.class, r, c, "round-rectangle"); }
    @Override protected VertexBatch vertices(Render2DCommand.RoundRect c) {
        return VertexBatches.round(c.bounds(), c.radiusTopLeft(), c.radiusTopRight(),
                c.radiusBottomRight(), c.radiusBottomLeft(), c.topLeft(), c.bottomLeft(),
                c.bottomRight(), c.topRight());
    }
}
