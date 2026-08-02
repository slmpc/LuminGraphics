package com.github.slmpc.lumingraphics.render.scheduler;

import com.github.slmpc.lumingraphics.core.geometry.CoordinateConverter;
import com.github.slmpc.lumingraphics.core.geometry.CoordinateOrigin;
import com.github.slmpc.lumingraphics.core.geometry.FramebufferRect;
import com.github.slmpc.lumingraphics.core.geometry.LogicalRect;
import com.github.slmpc.lumingraphics.core.geometry.SurfaceMetrics;

import java.util.Objects;
import java.util.function.Supplier;

/** 将 UI 逻辑坐标中的 Scissor 转为当前 framebuffer 坐标。 */
public interface Render2DScissorMapper {
    /** 返回用于逻辑坐标裁剪和空间索引的视口。 */
    Render2DBounds viewport(int framebufferWidth, int framebufferHeight);

    /** 返回 framebuffer 坐标中的 Scissor；完全位于 framebuffer 外时返回 null。 */
    Render2DScissor toFramebuffer(Render2DScissor logical, int framebufferWidth, int framebufferHeight);

    /** 保持既有直接使用 framebuffer 坐标的 scheduler 行为。 */
    static Render2DScissorMapper identity() {
        return IdentityHolder.INSTANCE;
    }

    /** 使用当前 surface 指标，将左上角原点的 UI 逻辑坐标映射到 OpenGL framebuffer。 */
    static Render2DScissorMapper topLeft(Supplier<SurfaceMetrics> metricsSupplier) {
        Objects.requireNonNull(metricsSupplier, "metricsSupplier");
        return new Render2DScissorMapper() {
            @Override
            public Render2DBounds viewport(int framebufferWidth, int framebufferHeight) {
                SurfaceMetrics metrics = metrics(metricsSupplier, framebufferWidth, framebufferHeight);
                var logical = metrics.logicalSize();
                return new Render2DBounds(0, 0, (float) logical.width(), (float) logical.height());
            }

            @Override
            public Render2DScissor toFramebuffer(Render2DScissor logical, int framebufferWidth, int framebufferHeight) {
                Objects.requireNonNull(logical, "logical");
                SurfaceMetrics metrics = metrics(metricsSupplier, framebufferWidth, framebufferHeight);
                FramebufferRect framebuffer = CoordinateConverter.toFramebufferScissor(
                        new LogicalRect(logical.x(), logical.y(), logical.width(), logical.height()),
                        metrics, CoordinateOrigin.TOP_LEFT,
                        com.github.slmpc.lumingraphics.core.geometry.ScissorPolicy.CLAMP_TO_FRAMEBUFFER);
                return framebuffer.isVisible()
                        ? new Render2DScissor(framebuffer.x(), framebuffer.y(), framebuffer.width(), framebuffer.height())
                        : null;
            }
        };
    }

    private static SurfaceMetrics metrics(Supplier<SurfaceMetrics> supplier, int width, int height) {
        SurfaceMetrics metrics = Objects.requireNonNull(supplier.get(), "metricsSupplier returned null");
        if (metrics.framebufferWidth() != width || metrics.framebufferHeight() != height) {
            throw new IllegalArgumentException("surface metrics and render execution dimensions differ");
        }
        return metrics;
    }

    final class IdentityHolder {
        private static final Render2DScissorMapper INSTANCE = new Render2DScissorMapper() {
            @Override
            public Render2DBounds viewport(int framebufferWidth, int framebufferHeight) {
                return new Render2DBounds(0, 0, framebufferWidth, framebufferHeight);
            }

            @Override
            public Render2DScissor toFramebuffer(Render2DScissor logical, int framebufferWidth, int framebufferHeight) {
                return Objects.requireNonNull(logical, "logical");
            }
        };

        private IdentityHolder() { }
    }
}
