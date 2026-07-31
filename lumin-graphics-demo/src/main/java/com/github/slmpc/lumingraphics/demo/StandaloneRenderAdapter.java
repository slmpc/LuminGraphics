package com.github.slmpc.lumingraphics.demo;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.lumingraphics.core.vertex.LuminVertexFormats;
import com.github.slmpc.lumingraphics.core.vertex.VertexSchema;
import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.lumingraphics.render.pipeline.LuminPipelineCatalog;
import com.github.slmpc.lumingraphics.render.renderer.RendererSet;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DBounds;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScheduler;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.lumingraphics.render.shader.FxaaShader;
import com.github.slmpc.lumingraphics.render.shader.ShaderArtifactLibrary;
import com.github.slmpc.lumingraphics.text.atlas.AtlasPixelFormat;
import com.github.slmpc.lumingraphics.text.atlas.AtlasPixels;
import com.github.slmpc.lumingraphics.text.font.FontResource;
import com.github.slmpc.lumingraphics.text.atlas.GlyphAtlasUpload;
import com.github.slmpc.lumingraphics.text.atlas.TtfFontLoader;
import com.github.slmpc.lumingraphics.text.render.TtfTextRenderer;
import com.github.slmpc.lumingraphics.ui.resource.UiResourceResolver;
import com.github.slmpc.lumingraphics.ui.theme.UiTheme;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import com.github.slmpc.lumingraphics.ui.render.LuminUiRenderer;
import com.github.slmpc.lumingraphics.ui.render.SchedulerTextBatchSink;
import com.github.slmpc.lumingraphics.ui.render.UiRenderBatch;
import com.github.slmpc.prismrhi.backend.BackendApi;
import com.github.slmpc.prismrhi.barrier.RhiImageBarrier;
import com.github.slmpc.prismrhi.barrier.RhiPipelineBarrier;
import com.github.slmpc.prismrhi.barrier.RhiResourceState;
import com.github.slmpc.prismrhi.command.RhiCommandBuffer;
import com.github.slmpc.prismrhi.command.RhiCommandBufferLevel;
import com.github.slmpc.prismrhi.command.RhiCommandPool;
import com.github.slmpc.prismrhi.command.RhiCommandPoolCreateInfo;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSet;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSetAllocateInfo;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSetLayout;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSetLayoutCreateInfo;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorStage;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorType;
import com.github.slmpc.prismrhi.device.RhiDevice;
import com.github.slmpc.prismrhi.format.RhiExtent3D;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.pipeline.RhiColorBlendAttachmentState;
import com.github.slmpc.prismrhi.pipeline.RhiCullMode;
import com.github.slmpc.prismrhi.pipeline.RhiFrontFace;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipeline;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipelineCreateInfo;
import com.github.slmpc.prismrhi.pipeline.RhiRasterizationState;
import com.github.slmpc.prismrhi.queue.RhiQueue;
import com.github.slmpc.prismrhi.queue.RhiQueueType;
import com.github.slmpc.prismrhi.queue.RhiSubmitInfo;
import com.github.slmpc.prismrhi.resource.RhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiBufferCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiBufferUsage;
import com.github.slmpc.prismrhi.resource.RhiImage;
import com.github.slmpc.prismrhi.resource.RhiImageCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiImageUsage;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiImageViewCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiMemoryUsage;
import com.github.slmpc.prismrhi.resource.RhiNativeObject;
import com.github.slmpc.prismrhi.resource.RhiNativeObjectType;
import com.github.slmpc.prismrhi.resource.RhiSampler;
import com.github.slmpc.prismrhi.resource.RhiSamplerCreateInfo;
import com.github.slmpc.prismrhi.shader.RhiShader;
import com.github.slmpc.prismrhi.shader.RhiShaderBinaryFormat;
import com.github.slmpc.prismrhi.shader.RhiShaderStage;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Demo-owned adapter that exercises the public Lumin render path on a real Prism device. */
final class StandaloneRenderAdapter implements RenderResources, UiResourceResolver, AutoCloseable {
    static final int WIDTH = 320;
    static final int HEIGHT = 192;
    private static final LuminColor BLUE = packedColor(.12f, .55f, .90f, 1);
    private static final LuminColor RED = packedColor(.92f, .28f, .30f, 1);
    private static final LuminColor YELLOW = packedColor(1, .83f, .24f, 1);

    private final RhiDevice device;
    private final RhiQueue queue;
    private final RhiFormat colorFormat;
    private final RhiBuffer frameBuffer;
    private final RhiBuffer fxaaBuffer;
    private final RhiDescriptorSetLayout frameLayout;
    private final RhiDescriptorSetLayout sampledLayout;
    private final RhiDescriptorSetLayout fxaaLayout;
    private final RhiDescriptorSet frameSet;
    private final RhiSampler sampler;
    private final Map<String, RhiShader> shaders = new LinkedHashMap<>();
    private final Map<String, RhiGraphicsPipeline> pipelines = new LinkedHashMap<>();
    private final Map<Render2DTexture, TextureResource> textures = new LinkedHashMap<>();
    private final IdentityHashMap<RhiGraphicsPipeline, String> pipelineIds = new IdentityHashMap<>();
    private final TtfFontLoader font;
    private final SchedulerTextBatchSink textSink;
    private final TtfTextRenderer textRenderer;
    private final Render2DScheduler scheduler;
    private final UiRenderBatch uiBatch;
    private final FxaaShader fxaa;
    private final Render2DTexture effectInput;
    private int textureSequence;

    StandaloneRenderAdapter(RhiDevice device, RhiFormat colorFormat) {
        this.device = device;
        this.queue = device.queue(RhiQueueType.GRAPHICS);
        this.colorFormat = colorFormat;
        float yScale = device.api() == BackendApi.VULKAN ? 2f / HEIGHT : -2f / HEIGHT;
        float yOffset = device.api() == BackendApi.VULKAN ? -1 : 1;
        frameBuffer = floatBuffer(new float[]{
                2f / WIDTH, 0, 0, 0, 0, yScale, 0, 0, 0, 0, 1, 0, -1, yOffset, 0, 1,
                WIDTH, HEIGHT, 1f / WIDTH, 1f / HEIGHT
        }, RhiBufferUsage.UNIFORM_BUFFER);
        fxaaBuffer = floatBuffer(new float[]{WIDTH, HEIGHT, 1f / WIDTH, 1f / HEIGHT}, RhiBufferUsage.UNIFORM_BUFFER);
        frameLayout = device.createDescriptorSetLayout(RhiDescriptorSetLayoutCreateInfo.builder()
                .binding(0, RhiDescriptorType.UNIFORM_BUFFER, 1, RhiDescriptorStage.VERTEX).build());
        sampledLayout = device.createDescriptorSetLayout(RhiDescriptorSetLayoutCreateInfo.builder()
                .binding(0, RhiDescriptorType.UNIFORM_BUFFER, 1, RhiDescriptorStage.VERTEX)
                .binding(1, RhiDescriptorType.COMBINED_IMAGE_SAMPLER, 1, RhiDescriptorStage.FRAGMENT).build());
        fxaaLayout = device.createDescriptorSetLayout(RhiDescriptorSetLayoutCreateInfo.builder()
                .binding(0, RhiDescriptorType.COMBINED_IMAGE_SAMPLER, 1, RhiDescriptorStage.FRAGMENT)
                .binding(1, RhiDescriptorType.UNIFORM_BUFFER, 1, RhiDescriptorStage.FRAGMENT).build());
        frameSet = device.allocateDescriptorSet(RhiDescriptorSetAllocateInfo.of(frameLayout));
        frameSet.update(writer -> writer.uniformBuffer(0, frameBuffer));
        sampler = device.createSampler(RhiSamplerCreateInfo.linearRepeat());
        createPipelines();
        effectInput = uploadRgba("fxaa-input", 32, 32, checkerboard(32, 32));
        font = new TtfFontLoader(FontResource.classpath("assets/lumin_graphics/fonts/font.ttf"),
                28, 3, 256, 128, 2, this::uploadAtlas, Runnable::run);
        textSink = new SchedulerTextBatchSink(this);
        textRenderer = new TtfTextRenderer(textSink);
        scheduler = new Render2DScheduler(RendererSet.create(this, 32 * 1024), 16);
        uiBatch = UiRenderBatch.owned(scheduler, 10, UiTheme.defaults(),
                new LuminUiRenderer(textRenderer, textSink, this));
        fxaa = new FxaaShader(this, 1024);
    }

    @Override public RhiDevice device() { return device; }

    @Override public RhiGraphicsPipeline requirePipeline(String id) {
        RhiGraphicsPipeline pipeline = pipelines.get(id);
        if (pipeline == null) throw new IllegalArgumentException("demo pipeline is unavailable: " + id);
        return pipeline;
    }

    @Override public RhiDescriptorSet requireTextureDescriptor(Render2DTexture texture) {
        TextureResource resource = textures.get(texture);
        if (resource == null) throw new IllegalArgumentException("demo texture is unavailable: " + texture);
        return resource.descriptor;
    }

    @Override public RhiDescriptorSet requireSegmentedShadowDescriptor(Render2DCommand.SegmentedShadow ignored) {
        throw new IllegalArgumentException("segmented shadows are not part of the standalone scene");
    }

    @Override public Render2DTexture texture(String id) {
        return textures.keySet().stream().filter(value -> value instanceof Render2DTexture.Resource resource
                && resource.id().equals(id)).findFirst().orElseThrow();
    }

    @Override public TtfFontLoader font(String id) { return font; }

    @Override public Render2DTexture atlasTexture(Object texture) {
        if (texture instanceof Render2DTexture value && textures.containsKey(value)) return value;
        throw new IllegalArgumentException("unknown standalone atlas texture");
    }

    Trace renderScene(RhiCommandBuffer delegate, boolean geometry, boolean ui, boolean text, boolean effect,
                      long frameId) {
        Trace trace = new Trace();
        RhiCommandBuffer commands = traced(delegate, trace);
        RenderExecution execution = new RenderExecution(commands, this, frameId, frameId - 1, WIDTH, HEIGHT);
        if (geometry) scheduler.layer(0).addRect(new Render2DBounds(20, 24, 116, 62), BLUE);
        UiTree tree = UiTree.build(scope -> {
            if (ui) scope.roundRect(151, 24, 148, 62, 10, RED);
            if (text) scope.text("Standalone", 168, 42, .62f, YELLOW);
        });
        uiBatch.render(tree);
        uiBatch.flush(execution);
        uiBatch.clear();
        if (effect) {
            commands.setScissor(com.github.slmpc.prismrhi.rendering.RhiRect2D.of(20, 105, 279, 51));
            trace.activeCategory = "effect";
            fxaa.apply(execution, effectInput);
            trace.activeCategory = null;
        }
        return trace;
    }

    private RhiCommandBuffer traced(RhiCommandBuffer delegate, Trace trace) {
        return (RhiCommandBuffer) Proxy.newProxyInstance(RhiCommandBuffer.class.getClassLoader(),
                new Class<?>[]{RhiCommandBuffer.class}, (proxy, method, args) -> {
                    try {
                        if (method.getName().equals("bindGraphicsPipeline")) {
                            RhiGraphicsPipeline pipeline = (RhiGraphicsPipeline) args[0];
                            String id = pipelineIds.get(pipeline);
                            trace.activeCategory = category(id);
                            trace.pipelineBinds.merge(trace.activeCategory, 1, Integer::sum);
                            Object result = method.invoke(delegate, args);
                            if (!id.equals("ttf-font-aa") && !id.equals("fxaa")) {
                                delegate.bindDescriptorSet(pipeline, 0, frameSet);
                            }
                            return result;
                        }
                        if (method.getName().startsWith("draw")) {
                            trace.drawCalls.merge(trace.activeCategory, 1, Integer::sum);
                        }
                        if (method.getName().equals("setScissor") && device.api() != BackendApi.VULKAN) {
                            var value = (com.github.slmpc.prismrhi.rendering.RhiRect2D) args[0];
                            args = new Object[]{com.github.slmpc.prismrhi.rendering.RhiRect2D.of(
                                    value.offset().x(), HEIGHT - value.offset().y() - value.extent().height(),
                                    value.extent().width(), value.extent().height())};
                        }
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
    }

    private static String category(String pipeline) {
        if (pipeline.equals("rectangle")) return "geometry";
        if (pipeline.equals("ttf-font-aa")) return "text";
        if (pipeline.equals("fxaa")) return "effect";
        return "ui";
    }

    private void createPipelines() {
        for (String id : List.of("rectangle", "round-rectangle", "ttf-font-aa", "fxaa")) {
            var descriptor = LuminPipelineCatalog.require(id);
            RhiShader vertex = shader(descriptor.vertex());
            RhiShader fragment = shader(descriptor.fragment());
            RhiGraphicsPipelineCreateInfo.Builder builder = RhiGraphicsPipelineCreateInfo.builder()
                    .shader(RhiShaderStage.VERTEX, vertex).shader(RhiShaderStage.FRAGMENT, fragment)
                    .descriptorSetLayout(id.equals("fxaa") ? fxaaLayout
                            : id.equals("ttf-font-aa") ? sampledLayout : frameLayout)
                    .rasterization(new RhiRasterizationState(null, RhiCullMode.NONE,
                            RhiFrontFace.COUNTER_CLOCKWISE, 1f))
                    .blend(RhiColorBlendAttachmentState.alphaBlend()).colorFormat(colorFormat);
            addVertexLayout(builder, descriptor.vertexLayout());
            RhiGraphicsPipeline pipeline = device.createGraphicsPipeline(builder.build());
            pipelines.put(id, pipeline);
            pipelineIds.put(pipeline, id);
        }
    }

    private RhiShader shader(LuminPipelineCatalog.ShaderRef reference) {
        return shaders.computeIfAbsent(reference.spirvPath(), ignored -> ShaderArtifactLibrary.create(device, reference,
                device.api() == BackendApi.VULKAN ? RhiShaderBinaryFormat.SPIRV : RhiShaderBinaryFormat.OPENGL_SOURCE));
    }

    private static void addVertexLayout(RhiGraphicsPipelineCreateInfo.Builder builder,
                                        LuminPipelineCatalog.VertexLayout layout) {
        if (layout == LuminPipelineCatalog.VertexLayout.FULLSCREEN) return;
        if (layout == LuminPipelineCatalog.VertexLayout.POSITION_COLOR) {
            builder.vertexBinding(0, 16).vertexAttribute(0, 0, RhiFormat.RGB32_FLOAT, 0)
                    .vertexAttribute(1, 0, RhiFormat.RGBA8_UNORM, 12);
            return;
        }
        if (layout == LuminPipelineCatalog.VertexLayout.POSITION_UV_COLOR) {
            builder.vertexBinding(0, 24).vertexAttribute(0, 0, RhiFormat.RGB32_FLOAT, 0)
                    .vertexAttribute(1, 0, RhiFormat.RG32_FLOAT, 12)
                    .vertexAttribute(2, 0, RhiFormat.RGBA8_UNORM, 20);
            return;
        }
        VertexSchema schema = switch (layout) {
            case ROUND_RECT -> LuminVertexFormats.ROUND_RECT;
            case ROUND_RECT_OUTLINE -> LuminVertexFormats.ROUND_RECT_OUTLINE;
            case TEXTURE -> LuminVertexFormats.TEXTURE;
            default -> throw new IllegalArgumentException("unsupported standalone layout: " + layout);
        };
        builder.vertexBinding(0, schema.stride());
        schema.elements().forEach(element -> builder.vertexAttribute(
                element.location(), 0, element.format(), element.offset()));
    }

    private GlyphAtlasUpload uploadAtlas(AtlasPixels pixels) {
        RhiFormat format = pixels.format() == AtlasPixelFormat.ALPHA8 ? RhiFormat.R8_UNORM : RhiFormat.RGBA8_UNORM;
        Render2DTexture texture = upload("atlas-" + (++textureSequence), pixels.width(), pixels.height(),
                format, pixels.data());
        return new GlyphAtlasUpload(texture, () -> removeTexture(texture));
    }

    private Render2DTexture uploadRgba(String id, int width, int height, byte[] data) {
        return upload(id, width, height, RhiFormat.RGBA8_UNORM, data);
    }

    private Render2DTexture upload(String id, int width, int height, RhiFormat format, byte[] data) {
        Render2DTexture key = new Render2DTexture.Resource(id);
        RhiBuffer staging = device.createBuffer(RhiBufferCreateInfo.builder(data.length)
                .usage(RhiBufferUsage.TRANSFER_SRC).memoryUsage(RhiMemoryUsage.CPU_TO_GPU).build());
        staging.write(ByteBuffer.allocateDirect(data.length).put(data).flip());
        RhiImage image = device.createImage(RhiImageCreateInfo.builder(RhiExtent3D.of2D(width, height))
                .format(format).usage(RhiImageUsage.TRANSFER_DST).usage(RhiImageUsage.SAMPLED).build());
        RhiImageView view = nativeForwardingView(device.createImageView(RhiImageViewCreateInfo.of(image)));
        RhiDescriptorSet descriptor = device.allocateDescriptorSet(RhiDescriptorSetAllocateInfo.of(
                id.equals("fxaa-input") ? fxaaLayout : sampledLayout));
        if (id.equals("fxaa-input")) {
            descriptor.update(writer -> writer.combinedImageSampler(0, 0, view, sampler).uniformBuffer(1, fxaaBuffer));
        } else {
            descriptor.update(writer -> writer.uniformBuffer(0, frameBuffer).combinedImageSampler(1, 0, view, sampler));
        }
        try (RhiCommandPool pool = device.createCommandPool(
                new RhiCommandPoolCreateInfo(RhiQueueType.GRAPHICS, true, true))) {
            RhiCommandBuffer command = pool.allocateCommandBuffer(RhiCommandBufferLevel.PRIMARY);
            command.begin();
            command.pipelineBarrier(RhiPipelineBarrier.builder()
                    .image(RhiImageBarrier.of(image, RhiResourceState.UNDEFINED, RhiResourceState.TRANSFER_DST)).build());
            command.copyBufferToImage(staging, image);
            command.pipelineBarrier(RhiPipelineBarrier.builder()
                    .image(RhiImageBarrier.of(image, RhiResourceState.TRANSFER_DST, RhiResourceState.SAMPLED_IMAGE)).build());
            command.end();
            queue.submit(RhiSubmitInfo.of(command));
            queue.waitIdle();
        } finally {
            staging.close();
        }
        textures.put(key, new TextureResource(descriptor, view, image));
        return key;
    }

    private RhiImageView nativeForwardingView(RhiImageView delegate) {
        if (device.api() == BackendApi.VULKAN) return delegate;
        return new RhiImageView() {
            @Override public BackendApi api() { return delegate.api(); }
            @Override public RhiImage image() { return delegate.image(); }
            @Override public RhiFormat format() { return delegate.format(); }
            @Override public Set<com.github.slmpc.prismrhi.resource.RhiImageAspect> aspects() {
                return delegate.aspects();
            }
            @Override public Optional<RhiNativeObject> getNativeObject(RhiNativeObjectType type) {
                return delegate.image().getNativeObject(type);
            }
            @Override public void close() { delegate.close(); }
        };
    }

    private RhiBuffer floatBuffer(float[] values, RhiBufferUsage usage) {
        RhiBuffer buffer = device.createBuffer(RhiBufferCreateInfo.builder((long) values.length * Float.BYTES)
                .usage(usage).memoryUsage(RhiMemoryUsage.CPU_TO_GPU).build());
        ByteBuffer bytes = ByteBuffer.allocateDirect(values.length * Float.BYTES).order(ByteOrder.nativeOrder());
        for (float value : values) bytes.putFloat(value);
        buffer.write(bytes.flip());
        return buffer;
    }

    private static byte[] checkerboard(int width, int height) {
        byte[] pixels = new byte[width * height * 4];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int index = (y * width + x) * 4;
            int mirroredY = Math.min(y, height - 1 - y);
            boolean light = ((x / 4) + (mirroredY / 4)) % 2 == 0;
            pixels[index] = (byte) (light ? 40 : 12);
            pixels[index + 1] = (byte) (light ? 220 : 92);
            pixels[index + 2] = (byte) (light ? 86 : 35);
            pixels[index + 3] = (byte) 255;
        }
        return pixels;
    }

    private static LuminColor packedColor(float red, float green, float blue, float alpha) {
        return new LuminColor(alpha, blue, green, red);
    }

    private void removeTexture(Render2DTexture key) {
        TextureResource resource = textures.remove(key);
        if (resource != null) resource.close();
    }

    @Override public void close() {
        fxaa.close();
        uiBatch.close();
        textRenderer.close();
        font.close();
        closeReverse(new ArrayList<>(textures.values()));
        textures.clear();
        closeReverse(new ArrayList<>(pipelines.values()));
        closeReverse(new ArrayList<>(shaders.values()));
        sampler.close();
        frameSet.close();
        fxaaLayout.close();
        sampledLayout.close();
        frameLayout.close();
        fxaaBuffer.close();
        frameBuffer.close();
    }

    private static void closeReverse(List<? extends AutoCloseable> resources) {
        for (int index = resources.size() - 1; index >= 0; index--) {
            try {
                resources.get(index).close();
            } catch (RuntimeException runtime) {
                throw runtime;
            } catch (Exception error) {
                throw new IllegalStateException("standalone resource cleanup failed", error);
            }
        }
    }

    static final class Trace {
        final Map<String, Integer> pipelineBinds = new LinkedHashMap<>();
        final Map<String, Integer> drawCalls = new LinkedHashMap<>();
        String activeCategory;
        int binds(String category) { return pipelineBinds.getOrDefault(category, 0); }
        int draws(String category) { return drawCalls.getOrDefault(category, 0); }
    }

    private record TextureResource(RhiDescriptorSet descriptor, RhiImageView view, RhiImage image)
            implements AutoCloseable {
        @Override public void close() { descriptor.close(); view.close(); image.close(); }
    }
}
