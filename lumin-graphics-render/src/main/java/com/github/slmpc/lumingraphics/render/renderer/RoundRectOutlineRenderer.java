package com.github.slmpc.lumingraphics.render.renderer;
import com.github.slmpc.lumingraphics.render.RenderResources;
import com.github.slmpc.lumingraphics.render.immediate.VertexBatch;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
public final class RoundRectOutlineRenderer extends AbstractRenderer<Render2DCommand.RoundRectOutline> {
    public RoundRectOutlineRenderer(RenderResources r, int c) { super(Render2DCommand.RoundRectOutline.class, r, c, "round-rectangle-outline"); }
    @Override protected VertexBatch vertices(Render2DCommand.RoundRectOutline c) {
        return VertexBatches.outline(c.shapeBounds(), c.radiusTopLeft(), c.radiusTopRight(),
                c.radiusBottomRight(), c.radiusBottomLeft(), c.width(), c.color());
    }
}
