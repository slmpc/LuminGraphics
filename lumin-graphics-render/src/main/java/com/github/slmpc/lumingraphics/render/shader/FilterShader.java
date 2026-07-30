package com.github.slmpc.lumingraphics.render.shader;
import com.github.slmpc.lumingraphics.render.RenderExecution;
import com.github.slmpc.lumingraphics.render.RenderResources;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
public final class FilterShader extends FullscreenEffect {
    public FilterShader(RenderResources resources, int capacity) { super(resources, capacity, "filter"); }
    public void apply(RenderExecution execution, Render2DTexture input) { applyEffect(execution, input); }
}
