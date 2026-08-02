package com.github.slmpc.lumingraphics.render;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.lumingraphics.core.geometry.SurfaceMetrics;
import com.github.slmpc.lumingraphics.render.renderer.RendererSet;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DBounds;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScissor;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScissorMapper;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScheduler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Render2DScissorMapperTest {
    @Test
    void mapsTopLeftLogicalScissorToScaledBottomLeftFramebuffer() {
        Render2DScissorMapper mapper = Render2DScissorMapper.topLeft(
                () -> new SurfaceMetrics(200, 100, 2.0));

        assertEquals(new Render2DScissor(20, 50, 60, 40),
                mapper.toFramebuffer(new Render2DScissor(10, 5, 30, 20), 200, 100));
        assertEquals(new Render2DBounds(0, 0, 100, 50), mapper.viewport(200, 100));
    }

    @Test
    void schedulerSubmitsMappedScissorWhileKeepingLogicalGeometry() {
        FakeRhi fake = new FakeRhi();
        Render2DScissorMapper mapper = Render2DScissorMapper.topLeft(
                () -> new SurfaceMetrics(200, 100, 2.0));
        try (Render2DScheduler scheduler = new Render2DScheduler(
                RendererSet.create(fake.resources(), 4096), 8, mapper)) {
            var layer = scheduler.layer(0);
            layer.pushScissor(new Render2DScissor(10, 5, 30, 20));
            layer.addRect(new Render2DBounds(10, 5, 30, 20), new LuminColor(1, 1, 1, 1));
            scheduler.flushAndClear(fake.execution(1, 0, 200, 100));
        }
        assertTrue(fake.trace().contains("scissor=20,50,60,40"));
    }
}
