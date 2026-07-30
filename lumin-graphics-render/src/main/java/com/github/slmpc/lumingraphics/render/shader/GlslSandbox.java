package com.github.slmpc.lumingraphics.render.shader;
import com.github.slmpc.lumingraphics.render.RenderExecution;
import com.github.slmpc.lumingraphics.render.RenderResources;
import com.github.slmpc.lumingraphics.render.pipeline.LuminPipelineCatalog;
public final class GlslSandbox extends FullscreenEffect {
    public GlslSandbox(RenderResources resources, int capacity, String pipeline) {
        super(resources, capacity, requireSandbox(pipeline));
    }
    public void apply(RenderExecution execution) { applyEffect(execution, null); }
    private static String requireSandbox(String id) {
        var descriptor = LuminPipelineCatalog.require(id);
        if (!descriptor.role().equals("sandbox effect")) throw new IllegalArgumentException("pipeline is not a sandbox effect: " + id);
        return id;
    }
}
