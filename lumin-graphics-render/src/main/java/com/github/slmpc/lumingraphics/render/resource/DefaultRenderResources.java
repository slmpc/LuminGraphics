package com.github.slmpc.lumingraphics.render.resource;

import com.github.slmpc.lumingraphics.render.pipeline.LuminPipelineCatalog;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.lumingraphics.render.shader.ShaderArtifactLibrary;
import com.github.slmpc.prismrhi.backend.BackendApi;
import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSet;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSetAllocateInfo;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSetLayout;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSetLayoutCreateInfo;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorStage;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorType;
import com.github.slmpc.prismrhi.device.RhiDevice;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.pipeline.RhiColorBlendAttachmentState;
import com.github.slmpc.prismrhi.pipeline.RhiCullMode;
import com.github.slmpc.prismrhi.pipeline.RhiDynamicRenderingState;
import com.github.slmpc.prismrhi.pipeline.RhiFrontFace;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipeline;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipelineCreateInfo;
import com.github.slmpc.prismrhi.pipeline.RhiRasterizationState;
import com.github.slmpc.prismrhi.resource.RhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiBufferCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiBufferUsage;
import com.github.slmpc.prismrhi.resource.RhiMemoryUsage;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiSampler;
import com.github.slmpc.prismrhi.shader.RhiShader;
import com.github.slmpc.prismrhi.shader.RhiShaderBinaryFormat;
import com.github.slmpc.prismrhi.shader.RhiShaderStage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lumin 默认管线、descriptor layout 与帧 uniform 服务。
 */
public final class DefaultRenderResources implements RenderResources, AutoCloseable {
    /**
     * {@code LuminFrame} 的 std140 大小：一个 {@code mat4} 和一个 {@code vec4}。
     */
    public static final int FRAME_UNIFORM_BYTES = 20 * Float.BYTES;
    private static final int SEGMENT_UNIFORM_BYTES = 16 + 64 * 16 + 64 * 16;

    private final RhiDevice device;
    private final RhiContextIdentity contextIdentity;
    private final RhiBuffer frameBuffer;
    private final RhiDescriptorSetLayout frameLayout;
    private final RhiDescriptorSetLayout sampledDrawLayout;
    private final RhiDescriptorSetLayout effectLayout;
    private final RhiDescriptorSetLayout menuLayout;
    private final RhiDescriptorSetLayout segmentedLayout;
    private final RhiDescriptorSet frameDescriptor;
    private final Map<String, RhiShader> shaders = new LinkedHashMap<>();
    private final Map<String, RhiGraphicsPipeline> pipelines = new LinkedHashMap<>();
    private final Map<Render2DTexture, RegisteredDescriptor> textures = new LinkedHashMap<>();
    private final IdentityHashMap<Render2DCommand.SegmentedShadow, SegmentedDescriptor> segmented = new IdentityHashMap<>();
    private boolean closed;

    public DefaultRenderResources(RhiDevice device, RhiFormat colorFormat, RhiFormat depthFormat) {
        this.device = Objects.requireNonNull(device, "device");
        this.contextIdentity = Objects.requireNonNull(device.contextIdentity(), "device context identity");
        frameBuffer = device.createBuffer(RhiBufferCreateInfo.builder(FRAME_UNIFORM_BYTES)
                .usage(RhiBufferUsage.UNIFORM_BUFFER).memoryUsage(RhiMemoryUsage.CPU_TO_GPU).build());
        frameLayout = device.createDescriptorSetLayout(layout()
                .binding(0, RhiDescriptorType.UNIFORM_BUFFER, 1, RhiDescriptorStage.VERTEX).build());
        sampledDrawLayout = device.createDescriptorSetLayout(layout()
                .binding(0, RhiDescriptorType.UNIFORM_BUFFER, 1, RhiDescriptorStage.VERTEX)
                .binding(1, RhiDescriptorType.COMBINED_IMAGE_SAMPLER, 1, RhiDescriptorStage.FRAGMENT).build());
        effectLayout = device.createDescriptorSetLayout(layout()
                .binding(0, RhiDescriptorType.COMBINED_IMAGE_SAMPLER, 1, RhiDescriptorStage.FRAGMENT)
                .binding(1, RhiDescriptorType.UNIFORM_BUFFER, 1, RhiDescriptorStage.FRAGMENT)
                .binding(2, RhiDescriptorType.UNIFORM_BUFFER, 1, RhiDescriptorStage.FRAGMENT).build());
        menuLayout = device.createDescriptorSetLayout(layout()
                .binding(0, RhiDescriptorType.UNIFORM_BUFFER, 1, RhiDescriptorStage.FRAGMENT).build());
        segmentedLayout = device.createDescriptorSetLayout(layout()
                .binding(0, RhiDescriptorType.UNIFORM_BUFFER, 1,
                        RhiDescriptorStage.VERTEX, RhiDescriptorStage.FRAGMENT).build());
        frameDescriptor = device.allocateDescriptorSet(RhiDescriptorSetAllocateInfo.of(frameLayout));
        frameDescriptor.update(writer -> writer.uniformBuffer(0, frameBuffer));
        try {
            createPipelines(colorFormat, depthFormat == null ? RhiFormat.UNDEFINED : depthFormat);
        } catch (RuntimeException failure) {
            try {
                close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public static List<String> demandedPipelineIds() {
        return LuminPipelineCatalog.entries().stream().map(LuminPipelineCatalog.PipelineDescriptor::id).toList();
    }

    @Override
    public RhiDevice device() {
        requireOpen();
        return device;
    }

    public RhiContextIdentity contextIdentity() {
        requireOpen();
        return contextIdentity;
    }

    @Override
    public RhiDescriptorSet requireFrameDescriptor() {
        requireOpen();
        return frameDescriptor;
    }

    /**
     * 使用 {@link #FRAME_UNIFORM_BYTES} 个调用方编码的字节更新 {@code LuminFrame}。
     *
     * <p>字节布局为 std140 的 {@code mat4 Projection} 后接 {@code vec4 Viewport}，
     * 因此该通用 API 不依赖某个数学库或平台类型。</p>
     */
    public void updateFrameUniforms(ByteBuffer uniforms) {
        requireOpen();
        ByteBuffer bytes = Objects.requireNonNull(uniforms, "uniforms").slice();
        if (bytes.remaining() != FRAME_UNIFORM_BYTES) {
            throw new IllegalArgumentException("frame uniforms must contain " + FRAME_UNIFORM_BYTES + " bytes");
        }
        frameBuffer.write(bytes);
    }

    @Override
    public RhiGraphicsPipeline requirePipeline(String id) {
        requireOpen();
        RhiGraphicsPipeline pipeline = pipelines.get(id);
        if (pipeline == null) {
            throw new RenderResourceException(RenderResourceException.Code.UNKNOWN_PIPELINE,
                    "Lumin pipeline is unavailable in context " + contextIdentity + ": " + id);
        }
        return pipeline;
    }

    public void registerTextureDescriptor(Render2DTexture texture, RhiDescriptorSet descriptor,
                                          RhiContextIdentity descriptorContext) {
        requireOpen();
        contextIdentity.requireSameContext(descriptorContext);
        textures.put(Objects.requireNonNull(texture, "texture"),
                new RegisteredDescriptor(Objects.requireNonNull(descriptor, "descriptor"), descriptorContext));
    }

    public RhiDescriptorSet createTextureDescriptor(RhiImageView view, RhiSampler sampler) {
        requireOpen();
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(sampler, "sampler");
        RhiDescriptorSet descriptor = device.allocateDescriptorSet(
                RhiDescriptorSetAllocateInfo.of(sampledDrawLayout));
        try {
            descriptor.update(writer -> writer.uniformBuffer(0, frameBuffer)
                    .combinedImageSampler(1, 0, view, sampler));
            return descriptor;
        } catch (RuntimeException failure) {
            try {
                descriptor.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public void unregisterTextureDescriptor(Render2DTexture texture, RhiDescriptorSet descriptor) {
        RegisteredDescriptor registered = textures.get(Objects.requireNonNull(texture, "texture"));
        if (registered != null && registered.descriptor() == descriptor) textures.remove(texture);
    }

    public boolean releaseTextureDescriptor(Render2DTexture texture, RhiDescriptorSet descriptor) {
        requireOpen();
        RegisteredDescriptor registered = textures.get(Objects.requireNonNull(texture, "texture"));
        if (registered == null || registered.descriptor() != descriptor) return false;
        textures.remove(texture);
        descriptor.close();
        return true;
    }

    @Override
    public RhiDescriptorSet requireTextureDescriptor(Render2DTexture texture) {
        requireOpen();
        RegisteredDescriptor registered = textures.get(texture);
        if (registered == null) {
            throw new RenderResourceException(RenderResourceException.Code.MISSING_DESCRIPTOR,
                    "Texture descriptor is unavailable in context " + contextIdentity + ": " + texture);
        }
        contextIdentity.requireSameContext(registered.contextIdentity());
        return registered.descriptor();
    }

    @Override
    public RhiDescriptorSet requireSegmentedShadowDescriptor(Render2DCommand.SegmentedShadow shadow) {
        requireOpen();
        return segmented.computeIfAbsent(Objects.requireNonNull(shadow, "shadow"), this::createSegmented).descriptor();
    }

    private SegmentedDescriptor createSegmented(Render2DCommand.SegmentedShadow shadow) {
        RhiBuffer buffer = device.createBuffer(RhiBufferCreateInfo.builder(SEGMENT_UNIFORM_BYTES)
                .usage(RhiBufferUsage.UNIFORM_BUFFER).memoryUsage(RhiMemoryUsage.CPU_TO_GPU).build());
        ByteBuffer bytes = ByteBuffer.allocateDirect(SEGMENT_UNIFORM_BYTES).order(ByteOrder.nativeOrder());
        bytes.putInt(shadow.segmentCount()).position(16);
        float[] rects = shadow.segmentRects();
        for (int index = 0; index < shadow.segmentCount(); index++) {
            int offset = index * 4;
            bytes.putFloat(rects[offset]).putFloat(rects[offset + 1])
                    .putFloat(rects[offset + 2]).putFloat(rects[offset + 3]);
        }
        bytes.position(16 + 64 * 16);
        float[] radii = shadow.segmentRadii();
        for (int index = 0; index < shadow.segmentCount(); index++) {
            bytes.putFloat(radii[index]).position(bytes.position() + 12);
        }
        bytes.position(0).limit(SEGMENT_UNIFORM_BYTES);
        buffer.write(bytes);
        RhiDescriptorSet descriptor = device.allocateDescriptorSet(RhiDescriptorSetAllocateInfo.of(segmentedLayout));
        descriptor.update(writer -> writer.uniformBuffer(0, buffer));
        return new SegmentedDescriptor(descriptor, buffer);
    }

    private void createPipelines(RhiFormat colorFormat, RhiFormat depthFormat) {
        if (colorFormat == null || colorFormat == RhiFormat.UNDEFINED) {
            throw new IllegalArgumentException("A concrete color target format is required");
        }
        for (LuminPipelineCatalog.PipelineDescriptor descriptor : LuminPipelineCatalog.entries()) {
            RhiGraphicsPipelineCreateInfo.Builder builder = RhiGraphicsPipelineCreateInfo.builder()
                    .shader(RhiShaderStage.VERTEX, shader(descriptor.vertex()))
                    .shader(RhiShaderStage.FRAGMENT, shader(descriptor.fragment()))
                    .descriptorSetLayout(layoutFor(descriptor))
                    .rendering(RhiDynamicRenderingState.builder().color(colorFormat).depth(depthFormat).build())
                    .topology(descriptor.topology())
                    .rasterization(new RhiRasterizationState(null, RhiCullMode.NONE,
                            RhiFrontFace.COUNTER_CLOCKWISE, 1f))
                    .blend(RhiColorBlendAttachmentState.alphaBlend());
            DefaultVertexLayouts.apply(builder, descriptor.vertexLayout());
            pipelines.put(descriptor.id(), device.createGraphicsPipeline(builder.build()));
        }
    }

    private RhiDescriptorSetLayout layoutFor(LuminPipelineCatalog.PipelineDescriptor descriptor) {
        if (descriptor.id().equals("segmented-shadow")) return segmentedLayout;
        if (descriptor.vertexLayout() == LuminPipelineCatalog.VertexLayout.FULLSCREEN) {
            return descriptor.samplers().isEmpty() ? menuLayout : effectLayout;
        }
        return descriptor.samplers().isEmpty() ? frameLayout : sampledDrawLayout;
    }

    private RhiShader shader(LuminPipelineCatalog.ShaderRef reference) {
        RhiShaderBinaryFormat format = device.api() == BackendApi.VULKAN
                ? RhiShaderBinaryFormat.SPIRV : RhiShaderBinaryFormat.OPENGL_SOURCE;
        return shaders.computeIfAbsent(reference.spirvPath() + ':' + format,
                ignored -> ShaderArtifactLibrary.create(device, reference, format));
    }

    private static RhiDescriptorSetLayoutCreateInfo.Builder layout() {
        return RhiDescriptorSetLayoutCreateInfo.builder();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        List<AutoCloseable> owned = new ArrayList<>();
        owned.add(frameBuffer);
        owned.add(frameLayout);
        owned.add(sampledDrawLayout);
        owned.add(effectLayout);
        owned.add(menuLayout);
        owned.add(segmentedLayout);
        owned.add(frameDescriptor);
        owned.addAll(shaders.values());
        owned.addAll(pipelines.values());
        owned.addAll(segmented.values());
        RuntimeException failure = null;
        for (int index = owned.size() - 1; index >= 0; index--) {
            try {
                owned.get(index).close();
            } catch (Exception exception) {
                RuntimeException next = exception instanceof RuntimeException runtime ? runtime
                        : new IllegalStateException("Failed to close Lumin render resource", exception);
                if (failure == null) failure = next;
                else failure.addSuppressed(next);
            }
        }
        segmented.clear();
        pipelines.clear();
        shaders.clear();
        textures.clear();
        if (failure != null) throw failure;
    }

    private void requireOpen() {
        if (closed) throw new RenderResourceException(RenderResourceException.Code.FRAME_STATE,
                "Lumin render resources are closed for context " + contextIdentity);
    }

    private record RegisteredDescriptor(RhiDescriptorSet descriptor, RhiContextIdentity contextIdentity) {
    }

    private record SegmentedDescriptor(RhiDescriptorSet descriptor, RhiBuffer buffer) implements AutoCloseable {
        @Override
        public void close() {
            descriptor.close();
            buffer.close();
        }
    }
}
