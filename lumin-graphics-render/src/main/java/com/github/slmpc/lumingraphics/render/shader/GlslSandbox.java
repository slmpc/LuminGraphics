package com.github.slmpc.lumingraphics.render.shader;
import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.lumingraphics.render.pipeline.LuminPipelineCatalog;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import java.nio.ByteBuffer;
public final class GlslSandbox extends FullscreenEffect {
    public GlslSandbox(RenderResources resources, int capacity, String pipeline) {
        super(resources, capacity, requireSandbox(pipeline));
    }
    /** Always fails because a sandbox application requires an explicit input and descriptor payload. */
    public void apply(RenderExecution execution) {
        throw new IllegalArgumentException("sandbox application requires an input and dynamic uniform descriptor");
    }
    public void apply(RenderExecution execution, Render2DTexture input, ByteBuffer uniforms) {
        applyEffect(execution, input, uniforms);
    }
    private static String requireSandbox(String id) {
        var descriptor = LuminPipelineCatalog.require(id);
        if (!descriptor.role().equals("sandbox effect")) throw new IllegalArgumentException("pipeline is not a sandbox effect: " + id);
        return id;
    }
}
