package com.github.slmpc.lumingraphics.render.shader;

import com.github.slmpc.prismrhi.command.RhiCommandBuffer;
import com.github.slmpc.prismrhi.rendering.RhiRenderingInfo;

import java.util.Objects;

/**
 * Selects whether an effect uses an existing pass or owns one dynamic-rendering pass.
 */
public sealed interface FullscreenEffectPass permits FullscreenEffectPass.External, FullscreenEffectPass.Rendering {
    void begin(RhiCommandBuffer commands);

    void end(RhiCommandBuffer commands);

    static FullscreenEffectPass external() {
        return External.INSTANCE;
    }

    static FullscreenEffectPass rendering(RhiRenderingInfo renderingInfo) {
        return new Rendering(renderingInfo);
    }

    final class External implements FullscreenEffectPass {
        private static final External INSTANCE = new External();

        private External() {
        }

        @Override
        public void begin(RhiCommandBuffer commands) {
            Objects.requireNonNull(commands, "commands");
        }

        @Override
        public void end(RhiCommandBuffer commands) {
            Objects.requireNonNull(commands, "commands");
        }
    }

    record Rendering(RhiRenderingInfo renderingInfo) implements FullscreenEffectPass {
        public Rendering {
            Objects.requireNonNull(renderingInfo, "renderingInfo");
        }

        @Override
        public void begin(RhiCommandBuffer commands) {
            Objects.requireNonNull(commands, "commands").beginRendering(renderingInfo);
        }

        @Override
        public void end(RhiCommandBuffer commands) {
            Objects.requireNonNull(commands, "commands").endRendering();
        }
    }
}
