package com.github.slmpc.lumingraphics.render;

import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.prismrhi.backend.BackendApi;
import com.github.slmpc.prismrhi.command.RhiCommandBuffer;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSet;
import com.github.slmpc.prismrhi.device.RhiDevice;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipeline;
import com.github.slmpc.prismrhi.resource.RhiBuffer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class FakeRhi {
    private final List<String> trace = new ArrayList<>();
    private final List<String> pipelines = new ArrayList<>();
    private int closedBuffers;
    private boolean failNextDraw;
    private String missingPipeline;
    private final RhiDevice device = createDevice();

    RhiDevice device() { return device; }

    private RhiDevice createDevice() {
        return proxy(RhiDevice.class, (self, method, args) -> switch (method.getName()) {
            case "api" -> BackendApi.VULKAN;
            case "createBuffer" -> buffer(((com.github.slmpc.prismrhi.resource.RhiBufferCreateInfo) args[0]).size());
            case "close" -> null;
            case "toString" -> "FakeRhiDevice";
            default -> defaultValue(method);
        });
    }

    RenderResources resources() {
        return new RenderResources() {
            @Override public RhiDevice device() { return FakeRhi.this.device(); }
            @Override public RhiGraphicsPipeline requirePipeline(String id) {
                if (id.equals(missingPipeline)) throw new IllegalStateException("missing pipeline: " + id);
                pipelines.add(id);
                trace.add("pipeline=" + id);
                return proxy(RhiGraphicsPipeline.class, FakeRhi::resourceCall);
            }
            @Override public RhiDescriptorSet requireTextureDescriptor(Render2DTexture texture) {
                String id = texture instanceof Render2DTexture.Resource resource ? resource.id() : "lumin";
                if (id.equals("missing")) throw new IllegalStateException("missing texture: " + id);
                trace.add("descriptor=" + id);
                return proxy(RhiDescriptorSet.class, FakeRhi::resourceCall);
            }
        };
    }

    RenderExecution execution(long frame, long complete, int width, int height) {
        return new RenderExecution(commandBuffer(), resources(), frame, complete, width, height);
    }

    List<String> trace() { return List.copyOf(trace); }
    List<String> boundPipelines() { return List.copyOf(pipelines); }
    int closedBuffers() { return closedBuffers; }
    void failNextDraw() { failNextDraw = true; }
    void missingPipeline(String id) { missingPipeline = id; }
    void record(String value) { trace.add(value); }

    private RhiBuffer buffer(long size) {
        AtomicBoolean closed = new AtomicBoolean();
        return proxy(RhiBuffer.class, (self, method, args) -> switch (method.getName()) {
            case "api" -> BackendApi.VULKAN;
            case "size" -> size;
            case "write" -> { trace.add("write=" + args[0] + ":" + ((ByteBuffer) args[1]).remaining()); yield null; }
            case "close" -> { if (closed.compareAndSet(false, true)) closedBuffers++; yield null; }
            default -> defaultValue(method);
        });
    }

    private RhiCommandBuffer commandBuffer() {
        return proxy(RhiCommandBuffer.class, (self, method, args) -> switch (method.getName()) {
            case "api" -> BackendApi.VULKAN;
            case "setScissor" -> {
                var rect = (com.github.slmpc.prismrhi.rendering.RhiRect2D) args[0];
                trace.add("scissor=" + rect.offset().x() + "," + rect.offset().y() + ","
                        + rect.extent().width() + "," + rect.extent().height());
                yield null;
            }
            case "bindVertexBuffer" -> { trace.add("vertexBuffer=" + args[2]); yield null; }
            case "begin" -> { trace.add("command.begin"); yield null; }
            case "end" -> { trace.add("command.end"); yield null; }
            case "bindGraphicsPipeline", "bindDescriptorSet", "setViewport", "close" -> null;
            case "draw" -> {
                if (failNextDraw) { failNextDraw = false; throw new IllegalStateException("backend draw failed"); }
                int vertices = args[0] instanceof Integer value ? value
                        : ((com.github.slmpc.prismrhi.command.RhiDrawCommand) args[0]).vertexCount();
                trace.add("draw=" + vertices);
                yield null;
            }
            case "level" -> com.github.slmpc.prismrhi.command.RhiCommandBufferLevel.PRIMARY;
            default -> defaultValue(method);
        });
    }

    private static Object resourceCall(Object self, Method method, Object[] args) {
        if (method.getName().equals("api")) return BackendApi.VULKAN;
        if (method.getName().equals("close")) return null;
        return defaultValue(method);
    }

    private static Object defaultValue(Method method) {
        if (method.isDefault()) return null;
        Class<?> type = method.getReturnType();
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == int.class) return 0;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
