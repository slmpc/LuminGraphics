package com.github.slmpc.lumingraphics.render.shader;
import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import java.nio.ByteBuffer;
public final class FxaaShader extends FullscreenEffect {
    public FxaaShader(RenderResources resources, int capacity) { super(resources, capacity, "fxaa"); }
    public void apply(RenderExecution execution, Render2DTexture input) {
        apply(execution, input, ByteBuffer.allocate(0));
    }
    public void apply(RenderExecution execution, Render2DTexture input, ByteBuffer uniforms) {
        applyEffect(execution, input, uniforms);
    }
}
