package com.github.slmpc.lumingraphics.core.context;

import com.github.slmpc.lumingraphics.core.exception.LuminResourceClosedException;
import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;
import com.github.slmpc.lumingraphics.core.geometry.SurfaceMetrics;
import com.github.slmpc.lumingraphics.core.resource.ResourceRegistry;
import com.github.slmpc.lumingraphics.core.target.RenderTarget;
import com.github.slmpc.lumingraphics.core.threading.RenderThreadGate;
import com.github.slmpc.prismrhi.device.RhiDevice;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Lumin 层的调用方拥有上下文。
 *
 * <p>该类型不创建或关闭 Prism 的 {@link RhiDevice}。调用方提供设备、渲染线程门禁以及每帧的
 * surface/目标查询函数；所有公开访问器都会验证当前线程和关闭状态。关闭本上下文仅关闭通过
 * {@link #resources()} 注册的资源。</p>
 */
public final class LuminGraphicsContext implements AutoCloseable {
    private final RhiDevice device;
    private final RenderThreadGate renderThread;
    private final Supplier<SurfaceMetrics> metricsSupplier;
    private final Supplier<RenderTarget> renderTargetSupplier;
    private final ResourceRegistry resources;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 创建与一个调用方 Prism 设备关联的上下文。
     *
     * @param device 已绑定有效上下文标识的 Prism 设备
     * @param renderThread 用于约束 Prism 调用的线程门禁
     * @param metricsSupplier 返回当前 framebuffer 指标的函数
     * @param renderTargetSupplier 返回与当前指标匹配的颜色目标的函数
     */
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

    /**
     * 返回调用方设备；只能在渲染线程调用。
     * @return 调用方提供的 Prism 设备
     */
    public RhiDevice device() {
        requireUsable();
        renderThread.requireRenderThread();
        return device;
    }

    /**
     * 返回当前 surface 指标；只能在渲染线程调用。
     * @return 当前 framebuffer 指标
     */
    public SurfaceMetrics metrics() {
        requireUsable();
        renderThread.requireRenderThread();
        SurfaceMetrics metrics = metricsSupplier.get();
        if (metrics == null) {
            throw new LuminValidationException("surface metrics supplier returned null");
        }
        return metrics;
    }

    /**
     * 返回当前帧渲染目标，并验证其上下文标识和尺寸与 {@link #metrics()} 一致。
     * @return 当前帧颜色渲染目标
     */
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

    /**
     * 返回由本上下文关闭的资源注册表；只能在渲染线程调用。
     * @return 本上下文的资源注册表
     */
    public ResourceRegistry resources() {
        requireUsable();
        renderThread.requireRenderThread();
        return resources;
    }

    /** 验证当前线程是渲染线程。 */
    public void requireRenderThread() {
        requireUsable();
        renderThread.requireRenderThread();
    }

    /**
     * 立即在当前渲染线程执行动作。
     * @param action 要执行的动作
     */
    public void runOnRenderThread(Runnable action) {
        requireUsable();
        renderThread.runNow(action);
    }

    /**
     * 将动作投递至渲染线程；执行时会再次验证上下文未关闭。
     * @param action 要投递的动作
     */
    public void executeOnRenderThread(Runnable action) {
        requireUsable();
        renderThread.execute(() -> {
            requireUsable();
            action.run();
        });
    }

    /** 使所有登记资源失效；调用方随后负责按自身策略重建它们。 */
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

    /**
     * 逆序关闭已登记资源。不会关闭调用方持有的 Prism 设备或实例。
     */
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
