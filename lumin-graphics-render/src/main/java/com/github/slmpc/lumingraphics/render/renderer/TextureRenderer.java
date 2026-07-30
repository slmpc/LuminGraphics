package com.github.slmpc.lumingraphics.render.renderer;
import com.github.slmpc.lumingraphics.render.RenderResources;
import com.github.slmpc.lumingraphics.render.immediate.VertexBatch;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
public final class TextureRenderer extends AbstractRenderer<Render2DCommand.Texture> {
    public TextureRenderer(RenderResources r, int c) { super(Render2DCommand.Texture.class, r, c, "texture"); }
    @Override protected VertexBatch vertices(Render2DCommand.Texture c) { return VertexBatches.texture(c.bounds(), c.color()); }
    @Override protected Render2DTexture texture(Render2DCommand.Texture c) { return c.texture(); }
}
