package com.github.slmpc.lumingraphics.render;

import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
import com.github.slmpc.lumingraphics.render.shader.FullscreenEffectBinding;
import com.github.slmpc.lumingraphics.render.shader.FullscreenEffectPass;
import com.github.slmpc.lumingraphics.render.shader.FullscreenEffectRequest;
import com.github.slmpc.prismrhi.backend.BackendApi;
import com.github.slmpc.prismrhi.command.RhiCommandBuffer;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSet;
import com.github.slmpc.prismrhi.device.RhiDevice;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipeline;
import com.github.slmpc.prismrhi.resource.RhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.rendering.RhiRect2D;
import com.github.slmpc.prismrhi.rendering.RhiRenderingAttachment;
import com.github.slmpc.prismrhi.rendering.RhiRenderingInfo;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

final class FakeRhi {
    private final List<String> trace = new ArrayList<>();
    private final List<String> pipelines = new ArrayList<>();
    private final List<byte[]> writes = new ArrayList<>();
    private final List<Render2DCommand.SegmentedShadow> segmentedPayloads = new ArrayList<>();
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
            @Override public RhiDescriptorSet requireFrameDescriptor() {
                trace.add("descriptor=frame");
                return proxy(RhiDescriptorSet.class, FakeRhi::resourceCall);
            }
            @Override public RhiDescriptorSet requireTextureDescriptor(Render2DTexture texture) {
                String id = texture instanceof Render2DTexture.Resource resource ? resource.id() : "lumin";
                if (id.equals("missing")) throw new IllegalStateException("missing texture: " + id);
                trace.add("descriptor=" + id);
                return proxy(RhiDescriptorSet.class, FakeRhi::resourceCall);
            }
            @Override public RhiDescriptorSet requireSegmentedShadowDescriptor(Render2DCommand.SegmentedShadow shadow) {
                segmentedPayloads.add(shadow);
                trace.add("segmentedDescriptor=" + shadow.segmentCount());
                return proxy(RhiDescriptorSet.class, FakeRhi::resourceCall);
            }
            @Override public FullscreenEffectBinding requireFullscreenEffectBinding(
                    FullscreenEffectRequest request, RenderExecution execution) {
                String id = request.input() instanceof Render2DTexture.Resource resource ? resource.id() : "lumin";
                if (id.equals("missing")) throw new IllegalStateException("missing effect input: " + id);
                trace.add("effectInput=" + id);
                trace.add("descriptor=" + id);
                trace.add("effectUniformBytes=" + request.uniforms().remaining());
                ByteBuffer uniformBytes = request.uniforms();
                byte[] encoded = new byte[uniformBytes.remaining()];
                uniformBytes.get(encoded);
                trace.add("effectUniformHex=" + HexFormat.of().formatHex(encoded));
                trace.add("effectDescriptor=" + request.pipelineId());
                RhiDescriptorSet descriptor = proxy(RhiDescriptorSet.class, FakeRhi::resourceCall);
                RhiImageView view = proxy(RhiImageView.class, FakeRhi::resourceCall);
                RhiRenderingInfo rendering = RhiRenderingInfo.builder(RhiRect2D.of(execution.width(), execution.height()))
                        .color(RhiRenderingAttachment.color(view)).build();
                return new FullscreenEffectBinding(descriptor, FullscreenEffectPass.rendering(rendering));
            }
        };
    }

    RenderExecution execution(long frame, long complete, int width, int height) {
        return new RenderExecution(commandBuffer(), resources(), frame, complete, width, height);
    }

    List<String> trace() { return List.copyOf(trace); }
    List<String> boundPipelines() { return List.copyOf(pipelines); }
    List<byte[]> writes() { return writes.stream().map(byte[]::clone).toList(); }
    List<Render2DCommand.SegmentedShadow> segmentedPayloads() { return List.copyOf(segmentedPayloads); }
    int closedBuffers() { return closedBuffers; }
    void failNextDraw() { failNextDraw = true; }
    void missingPipeline(String id) { missingPipeline = id; }
    void record(String value) { trace.add(value); }

    private RhiBuffer buffer(long size) {
        AtomicBoolean closed = new AtomicBoolean();
        return proxy(RhiBuffer.class, (self, method, args) -> switch (method.getName()) {
            case "api" -> BackendApi.VULKAN;
            case "size" -> size;
            case "write" -> {
                ByteBuffer source = ((ByteBuffer) args[1]).slice();
                byte[] bytes = new byte[source.remaining()];
                source.get(bytes);
                writes.add(bytes);
                trace.add("write=" + args[0] + ":" + bytes.length);
                yield null;
            }
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
            case "setPrimitiveTopology" -> {
                trace.add("topology=" + args[0]);
                yield null;
            }
            case "bindVertexBuffer" -> { trace.add("vertexBuffer=" + args[2]); yield null; }
            case "begin" -> { trace.add("command.begin"); yield null; }
            case "end" -> { trace.add("command.end"); yield null; }
            case "bindGraphicsPipeline", "bindDescriptorSet", "setViewport", "close" -> null;
            case "beginRendering" -> {
                var info = (RhiRenderingInfo) args[0];
                trace.add("render.begin=" + info.renderArea().extent().width() + "x"
                        + info.renderArea().extent().height());
                yield null;
            }
            case "endRendering" -> { trace.add("render.end"); yield null; }
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
