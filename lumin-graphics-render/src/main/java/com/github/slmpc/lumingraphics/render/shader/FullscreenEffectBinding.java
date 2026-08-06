package com.github.slmpc.lumingraphics.render.shader;

import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSet;

import java.util.Objects;

/**
 * Backend-resolved descriptor and render-pass selection for one fullscreen effect application.
 */
public record FullscreenEffectBinding(RhiDescriptorSet descriptor, FullscreenEffectPass pass) {
    public FullscreenEffectBinding {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(pass, "pass");
    }
}
