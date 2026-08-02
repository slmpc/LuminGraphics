package com.github.slmpc.lumingraphics.render.shader;
import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import java.nio.ByteBuffer;
public final class BlurShader extends FullscreenEffect {
    public BlurShader(RenderResources resources, int capacity) { super(resources, capacity, "blur"); }
    public void apply(RenderExecution execution, Render2DTexture input) {
        apply(execution, input, ByteBuffer.allocate(0));
    }
    public void apply(RenderExecution execution, Render2DTexture input, ByteBuffer uniforms) {
        applyEffect(execution, input, uniforms);
    }
}
