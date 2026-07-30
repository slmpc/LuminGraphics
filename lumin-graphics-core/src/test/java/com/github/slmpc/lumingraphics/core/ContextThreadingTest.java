package com.github.slmpc.lumingraphics.core;

import com.github.slmpc.lumingraphics.core.exception.LuminResourceClosedException;
import com.github.slmpc.lumingraphics.core.exception.LuminThreadException;
import com.github.slmpc.lumingraphics.core.geometry.SurfaceMetrics;
import com.github.slmpc.lumingraphics.testkit.RecordingRhiDevice;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiOwnership;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContextThreadingTest {
    @Test
    void rightThreadRunsExecutorMaintainsOrderAndWrongThreadFailsBeforeSupplier() throws Exception {
        RecordingRhiDevice fake = new RecordingRhiDevice(10, "context");
        Queue<Runnable> queued = new ArrayDeque<>();
        RenderThreadGate gate = new RenderThreadGate(Thread.currentThread(), queued::add);
        AtomicInteger supplierCalls = new AtomicInteger();
        SurfaceMetrics metrics = new SurfaceMetrics(640, 480, 2.0);
        RhiImageView color = fake.resource(RhiImageView.class, "target", RhiOwnership.BORROWED);
        RenderTarget target = new RenderTarget(color, Optional.empty(), 640, 480, fake.contextIdentity());
        LuminGraphicsContext context = new LuminGraphicsContext(
                fake.device(), gate,
                () -> {
                    supplierCalls.incrementAndGet();
                    return metrics;
                },
                () -> target
        );

        assertEquals(metrics, context.metrics());
        assertEquals(target, context.renderTarget());
        List<Integer> order = new ArrayList<>();
        context.executeOnRenderThread(() -> order.add(1));
        context.executeOnRenderThread(() -> order.add(2));
        queued.remove().run();
        queued.remove().run();
        assertEquals(List.of(1, 2), order);

        AtomicReference<Throwable> wrongThread = new AtomicReference<>();
        int callsBefore = supplierCalls.get();
        Thread thread = new Thread(() -> {
            try {
                context.metrics();
            } catch (Throwable failure) {
                wrongThread.set(failure);
            }
        }, "wrong-render-thread");
        thread.start();
        thread.join();
        assertEquals(LuminThreadException.class, wrongThread.get().getClass());
        assertEquals(callsBefore, supplierCalls.get());

        context.close();
        assertThrows(LuminResourceClosedException.class, context::resources);
        assertEquals(List.of("resource.create name=target ownership=BORROWED native=101"), fake.trace());
    }
}
