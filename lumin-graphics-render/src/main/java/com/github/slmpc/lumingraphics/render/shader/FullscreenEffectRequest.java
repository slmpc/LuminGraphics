package com.github.slmpc.lumingraphics.render.shader;

import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;

import java.nio.ByteBuffer;
import java.util.Objects;

/** Describes one fullscreen-effect input and its effect-specific dynamic uniform payload. */
public record FullscreenEffectRequest(String pipelineId, Render2DTexture input, ByteBuffer uniforms) {
    public FullscreenEffectRequest {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("pipelineId must not be blank");
        }
        Objects.requireNonNull(input, "input");
        ByteBuffer source = Objects.requireNonNull(uniforms, "uniforms").slice();
        ByteBuffer copy = ByteBuffer.allocateDirect(source.remaining()).order(source.order());
        copy.put(source).flip();
        uniforms = copy.asReadOnlyBuffer().order(copy.order());
    }

    @Override public ByteBuffer uniforms() {
        return uniforms.asReadOnlyBuffer().order(uniforms.order());
    }
}
