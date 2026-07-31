package com.github.slmpc.lumingraphics.render;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.lumingraphics.core.buffer.LuminRingBuffer;
import com.github.slmpc.lumingraphics.render.frame.RenderFrame;
import com.github.slmpc.lumingraphics.render.renderer.RendererSet;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DBounds;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScheduler;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScissor;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.lumingraphics.render.scheduler.Render3DScheduler;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Todo11BehaviorTest {
    @Test
    void ringRotatesWithoutOverwritingInFlightFramesAndRejectsOverflow() {
        FakeRhi fake = new FakeRhi();
        LuminRingBuffer ring = new LuminRingBuffer(fake.device(), 24, 3);

        ring.beginFrame(0, -1);
        var first = ring.write(ByteBuffer.wrap(new byte[8]), 8);
        ring.endFrame();
        ring.beginFrame(1, -1);
        ring.write(ByteBuffer.wrap(new byte[16]), 8);
        ring.endFrame();
        ring.beginFrame(2, -1);
        ring.write(ByteBuffer.wrap(new byte[24]), 8);
        ring.endFrame();

        assertEquals(0, first.slot());
        assertThrows(IllegalStateException.class, () -> ring.beginFrame(3, -1));
        ring.beginFrame(3, 0);
        assertThrows(IllegalArgumentException.class,
                () -> ring.write(ByteBuffer.wrap(new byte[25]), 1));
        assertThrows(IllegalStateException.class, ring::close);
        ring.endFrame();
        ring.close();
        assertEquals(3, fake.closedBuffers());
    }

    @Test
    void schedulerSortsLayersStablyCullsAndIntersectsNestedScissors() {
        FakeRhi fake = new FakeRhi();
        RendererSet renderers = RendererSet.create(fake.resources(), 4096);
        Render2DScheduler scheduler = new Render2DScheduler(renderers, 1);
        LuminColor white = new LuminColor(1, 1, 1, 1);

        scheduler.layer(5).addRect(new Render2DBounds(2, 2, 10, 10), white);
        var lower = scheduler.layer(-2);
        lower.pushScissor(new Render2DScissor(0, 0, 12, 12));
        lower.pushScissor(new Render2DScissor(5, 4, 20, 7));
        lower.addTexture(new Render2DBounds(6, 5, 4, 4), new Render2DTexture.Resource("atlas"), white);
        lower.popScissor();
        lower.popScissor();
        lower.addRect(new Render2DBounds(200, 200, 5, 5), white);
        scheduler.layer(5).addTriangle(8, 8, 3, white);

        scheduler.flush(fake.execution(7, 4, 32, 24));

        assertEquals(List.of("texture", "rectangle", "triangle"), fake.boundPipelines());
        assertTrue(fake.trace().contains("scissor=5,4,7,7"));
        assertTrue(fake.trace().contains("descriptor=atlas"));
        assertFalse(fake.trace().stream().anyMatch(line -> line.contains("200.0")));
        assertThrows(IllegalStateException.class, () -> scheduler.flush(fake.execution(7, 4, 32, 24)));
        scheduler.clear();
        assertTrue(scheduler.isEmpty());
        scheduler.flush(fake.execution(8, 7, 32, 24));
        scheduler.close();
    }

    @Test
    void flushAndClearCleansRendererFramesAfterBackendFailure() {
        FakeRhi fake = new FakeRhi();
        RendererSet renderers = RendererSet.create(fake.resources(), 1024);
        Render2DScheduler scheduler = new Render2DScheduler(renderers, 8);
        scheduler.layer(0).addRect(new Render2DBounds(0, 0, 2, 2), new LuminColor(1, 0, 0, 1));
        fake.failNextDraw();

        var commands = fake.execution(1, 0, 10, 10).commands();
        assertThrows(IllegalStateException.class, () -> {
            try (RenderFrame frame = new RenderFrame(commands, fake.resources(), 1, 0, 10, 10)) {
                scheduler.flushAndClear(frame.execution());
            }
        });
        assertTrue(scheduler.isEmpty());
        assertTrue(renderers.allFramesEnded());
        assertTrue(fake.trace().contains("command.end"));
        scheduler.close();
    }

    @Test
    void threeDimensionalCommandsUsePriorityThenStableInsertionOrder() {
        FakeRhi fake = new FakeRhi();
        Render3DScheduler scheduler = new Render3DScheduler();
        scheduler.add(10, execution -> fake.record("3d=late-a"));
        scheduler.add(-4, execution -> fake.record("3d=first"));
        scheduler.add(10, execution -> fake.record("3d=late-b"));

        scheduler.flushAndClear(fake.execution(1, 0, 20, 20));

        assertEquals(List.of("3d=first", "3d=late-a", "3d=late-b"),
                fake.trace().stream().filter(line -> line.startsWith("3d=")).toList());
        assertTrue(scheduler.isEmpty());
    }

    @Test
    void threeDimensionalFlushAndClearDropsCommandsAfterActionFailure() {
        FakeRhi fake = new FakeRhi();
        Render3DScheduler scheduler = new Render3DScheduler();
        scheduler.add(0, execution -> { throw new IllegalStateException("3D backend failed"); });
        assertThrows(IllegalStateException.class,
                () -> scheduler.flushAndClear(fake.execution(1, 0, 20, 20)));
        assertTrue(scheduler.isEmpty());
    }

    @Test
    void clearLayerPreservesOtherLayersAndResetsOnlyItsScissor() {
        FakeRhi fake = new FakeRhi();
        Render2DScheduler scheduler = new Render2DScheduler(RendererSet.create(fake.resources(), 1024), 8);
        scheduler.layer(1).addRect(new Render2DBounds(1, 1, 2, 2), new LuminColor(1, 1, 1, 1));
        scheduler.layer(2).addTriangle(4, 4, 1, new LuminColor(1, 1, 1, 1));
        scheduler.clearLayer(1);
        scheduler.flushAndClear(fake.execution(1, 0, 20, 20));
        assertEquals(List.of("triangle"), fake.boundPipelines());
        scheduler.close();
    }
}
