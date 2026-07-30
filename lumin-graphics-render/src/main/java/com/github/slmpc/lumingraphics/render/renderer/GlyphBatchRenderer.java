package com.github.slmpc.lumingraphics.render.renderer;
import com.github.slmpc.lumingraphics.render.RenderResources;
import com.github.slmpc.lumingraphics.render.immediate.VertexBatch;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
public final class GlyphBatchRenderer extends AbstractRenderer<Render2DCommand.Glyphs> {
    public GlyphBatchRenderer(RenderResources r, int c) { super(Render2DCommand.Glyphs.class, r, c, "ttf-font-aa"); }
    @Override protected VertexBatch vertices(Render2DCommand.Glyphs c) { return VertexBatches.textured(c.glyphs()); }
    @Override protected Render2DTexture texture(Render2DCommand.Glyphs c) { return c.texture(); }
}
