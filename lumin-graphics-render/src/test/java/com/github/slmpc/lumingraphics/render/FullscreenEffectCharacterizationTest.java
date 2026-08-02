package com.github.slmpc.lumingraphics.render;

import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.lumingraphics.render.shader.BlurShader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FullscreenEffectCharacterizationTest {
    @Test
    void blurCurrentlyResolvesTheSuppliedTextureAndDrawsOneFullscreenTriangle() {
        FakeRhi fake = new FakeRhi();
        try (BlurShader blur = new BlurShader(fake.resources(), 256)) {
            blur.apply(fake.execution(1, 0, 64, 32), new Render2DTexture.Resource("scene-color"));
        }

        assertEquals(1, fake.trace().stream().filter("descriptor=scene-color"::equals).count());
        assertEquals(1, fake.trace().stream().filter("draw=3"::equals).count());
    }
}
