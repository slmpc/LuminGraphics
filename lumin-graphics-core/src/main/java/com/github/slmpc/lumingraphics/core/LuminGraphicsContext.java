package com.github.slmpc.lumingraphics.core;

import com.github.slmpc.lumingraphics.core.exception.LuminResourceClosedException;
import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;
import com.github.slmpc.lumingraphics.core.geometry.SurfaceMetrics;
import com.github.slmpc.lumingraphics.core.resource.ResourceRegistry;
import com.github.slmpc.prismrhi.device.RhiDevice;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class LuminGraphicsContext implements AutoCloseable {
    private final RhiDevice device;
    private final RenderThreadGate renderThread;
    private final Supplier<SurfaceMetrics> metricsSupplier;
    private final Supplier<RenderTarget> renderTargetSupplier;
    private final ResourceRegistry resources;
    private final AtomicBoolean closed = new AtomicBoolean();

    public LuminGraphicsContext(
            RhiDevice device,
            RenderThreadGate renderThread,
            Supplier<SurfaceMetrics> metricsSupplier,
            Supplier<RenderTarget> renderTargetSupplier
    ) {
        if (device == null || renderThread == null || metricsSupplier == null || renderTargetSupplier == null) {
            throw new LuminValidationException("graphics context dependencies must not be null");
        }
        if (device.contextIdentity() == null) {
            throw new LuminValidationException("RHI device context identity must not be null");
        }
        this.device = device;
        this.renderThread = renderThread;
        this.metricsSupplier = metricsSupplier;
        this.renderTargetSupplier = renderTargetSupplier;
        this.resources = new ResourceRegistry(device.contextIdentity());
    }

    public RhiDevice device() {
        requireUsable();
        renderThread.requireRenderThread();
        return device;
    }

    public SurfaceMetrics metrics() {
        requireUsable();
        renderThread.requireRenderThread();
        SurfaceMetrics metrics = metricsSupplier.get();
        if (metrics == null) {
            throw new LuminValidationException("surface metrics supplier returned null");
        }
        return metrics;
    }

    public RenderTarget renderTarget() {
        requireUsable();
        renderThread.requireRenderThread();
        RenderTarget target = renderTargetSupplier.get();
        if (target == null) {
            throw new LuminValidationException("render target supplier returned null");
        }
        target.requireContext(device.contextIdentity());
        SurfaceMetrics metrics = metrics();
        if (target.width() != metrics.framebufferWidth() || target.height() != metrics.framebufferHeight()) {
            throw new LuminValidationException("render target and surface metrics dimensions differ");
        }
        return target;
    }

    public ResourceRegistry resources() {
        requireUsable();
        renderThread.requireRenderThread();
        return resources;
    }

    public void requireRenderThread() {
        requireUsable();
        renderThread.requireRenderThread();
    }

    public void runOnRenderThread(Runnable action) {
        requireUsable();
        renderThread.runNow(action);
    }

    public void executeOnRenderThread(Runnable action) {
        requireUsable();
        renderThread.execute(() -> {
            requireUsable();
            action.run();
        });
    }

    public void invalidateResources() {
        requireUsable();
        renderThread.requireRenderThread();
        resources.invalidateAll();
    }

    private void requireUsable() {
        if (closed.get()) {
            throw new LuminResourceClosedException("graphics context is closed");
        }
    }

    @Override
    public void close() {
        if (!closed.get()) {
            renderThread.requireRenderThread();
        }
        if (closed.compareAndSet(false, true)) {
            resources.close();
        }
    }
}
