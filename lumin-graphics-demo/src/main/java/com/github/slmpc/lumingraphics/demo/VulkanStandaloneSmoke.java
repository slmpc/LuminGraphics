package com.github.slmpc.lumingraphics.demo;

import com.github.slmpc.lumingraphics.render.pipeline.LuminPipelineCatalog;
import com.github.slmpc.lumingraphics.render.shader.ShaderArtifactLibrary;
import com.github.slmpc.lumingraphics.text.font.FontResource;
import com.github.slmpc.prismrhi.PrismRHI;
import com.github.slmpc.prismrhi.backend.BackendFeature;
import com.github.slmpc.prismrhi.backend.vulkan.VulkanBackendProvider;
import com.github.slmpc.prismrhi.backend.vulkan.VulkanNativeObjectTypes;
import com.github.slmpc.prismrhi.barrier.RhiImageBarrier;
import com.github.slmpc.prismrhi.barrier.RhiPipelineBarrier;
import com.github.slmpc.prismrhi.barrier.RhiResourceState;
import com.github.slmpc.prismrhi.command.RhiCommandBuffer;
import com.github.slmpc.prismrhi.command.RhiCommandBufferLevel;
import com.github.slmpc.prismrhi.command.RhiCommandPoolCreateInfo;
import com.github.slmpc.prismrhi.device.RhiDevice;
import com.github.slmpc.prismrhi.device.RhiDeviceCreateInfo;
import com.github.slmpc.prismrhi.format.RhiExtent3D;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.instance.RhiInstanceCreateInfo;
import com.github.slmpc.prismrhi.queue.RhiQueue;
import com.github.slmpc.prismrhi.queue.RhiQueueType;
import com.github.slmpc.prismrhi.queue.RhiSubmitInfo;
import com.github.slmpc.prismrhi.rendering.RhiRect2D;
import com.github.slmpc.prismrhi.rendering.RhiRenderingAttachment;
import com.github.slmpc.prismrhi.rendering.RhiRenderingInfo;
import com.github.slmpc.prismrhi.rendering.RhiViewport;
import com.github.slmpc.prismrhi.resource.RhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiBufferCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiBufferUsage;
import com.github.slmpc.prismrhi.resource.RhiImage;
import com.github.slmpc.prismrhi.resource.RhiImageCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiImageUsage;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiImageViewCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiMemoryUsage;
import com.github.slmpc.prismrhi.shader.RhiShader;
import com.github.slmpc.prismrhi.shader.RhiShaderBinaryFormat;
import com.github.slmpc.prismrhi.swapchain.RhiSwapchainCreateInfo;
import com.github.slmpc.prismrhi.sync.RhiPipelineStage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Real Vulkan render/readback/present path driven through the public Lumin renderer. */
final class VulkanStandaloneSmoke {
    private static final int WIDTH = StandaloneRenderAdapter.WIDTH;
    private static final int HEIGHT = StandaloneRenderAdapter.HEIGHT;

    private VulkanStandaloneSmoke() { }

    static void run(Path evidence, FontResource font) throws Exception {
        Files.createDirectories(evidence);
        StringBuilder log = new StringBuilder();
        try (CallerOwnedVulkanContext caller = CallerOwnedVulkanContext.create(WIDTH, HEIGHT)) {
            var instance = PrismRHI.createInstance(new VulkanBackendProvider(caller.externalContext()),
                    RhiInstanceCreateInfo.builder().applicationName("Lumin Vulkan standalone").build());
            var device = instance.createDevice(instance.enumeratePhysicalDevices().get(0),
                    RhiDeviceCreateInfo.builder().debugName("Lumin Vulkan smoke")
                            .enableFeature(BackendFeature.DYNAMIC_RENDERING).build());
            var queue = device.queue(RhiQueueType.GRAPHICS);
            var swapchain = device.createSwapchain(RhiSwapchainCreateInfo.builder(WIDTH, HEIGHT)
                    .preferredImageCount(2).preferredFormat(RhiFormat.BGRA8_SRGB).vsync(true).build());
            RhiImage output = device.createImage(RhiImageCreateInfo.builder(RhiExtent3D.of2D(WIDTH, HEIGHT))
                    .format(RhiFormat.BGRA8_UNORM).usage(RhiImageUsage.COLOR_ATTACHMENT)
                    .usage(RhiImageUsage.TRANSFER_SRC).build());
            RhiImageView outputView = device.createImageView(RhiImageViewCreateInfo.of(output));
            RhiBuffer readback = device.createBuffer(RhiBufferCreateInfo.builder((long) WIDTH * HEIGHT * 4)
                    .usage(RhiBufferUsage.TRANSFER_DST).memoryUsage(RhiMemoryUsage.GPU_TO_CPU).build());
            List<RhiShader> catalogShaders = createAllShaders(device, log);
            var pool = device.createCommandPool(new RhiCommandPoolCreateInfo(RhiQueueType.GRAPHICS, true, true));
            var command = pool.allocateCommandBuffer(RhiCommandBufferLevel.PRIMARY);
            var available = device.createSemaphore();
            var finished = device.createSemaphore();
            try (var adapter = new StandaloneRenderAdapter(device, RhiFormat.BGRA8_UNORM, font)) {
                Variant canonical = renderVariant(caller, queue, adapter, command, output, outputView, readback,
                        true, true, true, true, 1, true);
                Map<String, Integer> contributions = new LinkedHashMap<>();
                int frame = 2;
                for (String category : new String[]{"geometry", "ui", "text", "effect"}) {
                    Variant mutation = renderVariant(caller, queue, adapter, command, output, outputView, readback,
                            !category.equals("geometry"), !category.equals("ui"), !category.equals("text"),
                            !category.equals("effect"), frame++, false);
                    contributions.put(category, contribution(canonical.pixels, mutation.pixels));
                }
                requireCategories(canonical.trace, contributions);

                int imageIndex = swapchain.acquireNextImage(available);
                var swapImage = swapchain.image(imageIndex);
                try (var presentAdapter = new StandaloneRenderAdapter(device, swapImage.view().format(), font)) {
                    command.reset();
                    command.begin();
                    command.pipelineBarrier(RhiPipelineBarrier.builder().image(RhiImageBarrier.of(
                            swapImage.image(), RhiResourceState.UNDEFINED, RhiResourceState.COLOR_ATTACHMENT)).build());
                    command.beginRendering(RhiRenderingInfo.builder(RhiRect2D.of(WIDTH, HEIGHT))
                            .color(RhiRenderingAttachment.clearColor(swapImage.view(), .05f, .07f, .10f, 1)).build());
                    command.setViewport(RhiViewport.of(WIDTH, HEIGHT));
                    presentAdapter.renderScene(command, true, true, true, true, frame);
                    command.endRendering();
                    command.pipelineBarrier(RhiPipelineBarrier.builder().image(RhiImageBarrier.of(
                            swapImage.image(), RhiResourceState.COLOR_ATTACHMENT, RhiResourceState.PRESENT)).build());
                    command.end();
                    queue.submit(RhiSubmitInfo.builder().wait(available, RhiPipelineStage.COLOR_ATTACHMENT_OUTPUT)
                            .commandBuffer(command).signal(finished).build());
                    swapchain.present(queue, imageIndex, finished);
                    queue.waitIdle();
                }

                Path png = evidence.resolve("vulkan-canonical.png");
                Metrics metrics = writeAndAnalyze(canonical.pixels, png);
                if (metrics.nonblank < WIDTH * HEIGHT / 5 || metrics.colors < 5) {
                    throw new IllegalStateException("Vulkan canonical readback is blank or incomplete: " + metrics);
                }
                String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(png)));
                Files.writeString(evidence.resolve("vulkan-canonical.json"), json(caller, metrics, sha,
                        canonical.trace, contributions));
                log.append("PASS realVulkan=true rendered=true readback=true presented=true shaders=37 ")
                        .append("catalogPipelines=").append(LuminPipelineCatalog.entries().size())
                        .append(" nonblank=").append(metrics.nonblank).append('\n');
            } finally {
                device.waitIdle();
                finished.close();
                available.close();
                pool.close();
                for (int i = catalogShaders.size() - 1; i >= 0; i--) catalogShaders.get(i).close();
                readback.close();
                outputView.close();
                output.close();
                swapchain.close();
                device.close();
                instance.close();
            }
        } finally {
            log.append("CLEANUP_PASS prism=true vulkanDevice=true surface=true window=true glfw=true\n");
            Files.writeString(evidence.resolve("vulkan-smoke.log"), log);
        }
    }

    private static Variant renderVariant(CallerOwnedVulkanContext caller, RhiQueue queue,
                                         StandaloneRenderAdapter adapter, RhiCommandBuffer command,
                                         RhiImage output, RhiImageView outputView, RhiBuffer readback,
                                         boolean geometry, boolean ui, boolean text, boolean effect,
                                         long frameId, boolean first) {
        if (!first) command.reset();
        command.begin();
        if (first) command.pipelineBarrier(RhiPipelineBarrier.builder().image(RhiImageBarrier.of(
                output, RhiResourceState.UNDEFINED, RhiResourceState.COLOR_ATTACHMENT)).build());
        command.beginRendering(RhiRenderingInfo.builder(RhiRect2D.of(WIDTH, HEIGHT))
                .color(RhiRenderingAttachment.clearColor(outputView, .05f, .07f, .10f, 1)).build());
        command.setViewport(RhiViewport.of(WIDTH, HEIGHT));
        StandaloneRenderAdapter.Trace trace = adapter.renderScene(
                command, geometry, ui, text, effect, frameId);
        command.endRendering();
        command.end();
        queue.submit(RhiSubmitInfo.of(command));
        queue.waitIdle();
        long imageHandle = output.getNativeObject(VulkanNativeObjectTypes.IMAGE).orElseThrow().value();
        long bufferHandle = readback.getNativeObject(VulkanNativeObjectTypes.BUFFER).orElseThrow().value();
        caller.copyImageToBuffer(imageHandle, bufferHandle);
        ByteBuffer mapped = readback.map();
        try {
            byte[] pixels = new byte[WIDTH * HEIGHT * 4];
            mapped.get(0, pixels);
            return new Variant(pixels, trace);
        } finally {
            readback.unmap();
        }
    }

    private static int contribution(byte[] canonical, byte[] mutation) {
        int changed = 0;
        for (int index = 0; index < canonical.length; index += 4) {
            if (canonical[index] != mutation[index] || canonical[index + 1] != mutation[index + 1]
                    || canonical[index + 2] != mutation[index + 2] || canonical[index + 3] != mutation[index + 3]) {
                changed++;
            }
        }
        return changed;
    }

    private static void requireCategories(StandaloneRenderAdapter.Trace trace, Map<String, Integer> contributions) {
        for (String category : new String[]{"geometry", "ui", "text", "effect"}) {
            if (trace.binds(category) <= 0 || trace.draws(category) <= 0 || contributions.get(category) <= 0) {
                throw new IllegalStateException(category + " has no real Vulkan bind/draw/pixel contribution");
            }
        }
    }

    private static List<RhiShader> createAllShaders(RhiDevice device, StringBuilder log) {
        List<RhiShader> shaders = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (var pipeline : LuminPipelineCatalog.entries()) {
            for (var reference : List.of(pipeline.vertex(), pipeline.fragment())) {
                if (seen.add(reference.spirvPath())) {
                    shaders.add(ShaderArtifactLibrary.create(device, reference, RhiShaderBinaryFormat.SPIRV));
                    log.append("SHADER path=").append(reference.spirvPath()).append(" created=true\n");
                }
            }
            log.append("PIPELINE_CATALOG id=").append(pipeline.id()).append(" artifactsReady=true\n");
        }
        if (seen.size() != 37) throw new IllegalStateException("expected 37 SPIR-V stages, got " + seen.size());
        return shaders;
    }

    private static Metrics writeAndAnalyze(byte[] pixels, Path path) throws Exception {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        int nonblank = 0, minX = WIDTH, minY = HEIGHT, maxX = -1, maxY = -1;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int i = (y * WIDTH + x) * 4;
            int b = Byte.toUnsignedInt(pixels[i]);
            int g = Byte.toUnsignedInt(pixels[i + 1]);
            int r = Byte.toUnsignedInt(pixels[i + 2]);
            int a = Byte.toUnsignedInt(pixels[i + 3]);
            int rgb = (r << 16) | (g << 8) | b;
            colors.add(rgb);
            if (r + g + b > 32) {
                nonblank++;
                minX = Math.min(minX, x); minY = Math.min(minY, y);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            }
            image.setRGB(x, y, (a << 24) | rgb);
        }
        ImageIO.write(image, "png", path.toFile());
        return new Metrics(nonblank, colors.size(), minX, minY, maxX, maxY);
    }

    private static String json(CallerOwnedVulkanContext caller, Metrics metrics, String sha,
                               StandaloneRenderAdapter.Trace trace, Map<String, Integer> contributions) {
        StringBuilder categories = new StringBuilder();
        int index = 0;
        for (String category : new String[]{"geometry", "ui", "text", "effect"}) {
            categories.append("    \"").append(category).append("\": {\"realPipelineBinds\": ")
                    .append(trace.binds(category)).append(", \"realDrawCalls\": ").append(trace.draws(category))
                    .append(", \"pixelContribution\": ").append(contributions.get(category)).append("}")
                    .append(++index == 4 ? "\n" : ",\n");
        }
        return "{\n  \"backend\": \"vulkan\",\n  \"width\": " + WIDTH + ",\n  \"height\": " + HEIGHT
                + ",\n  \"nonblankCount\": " + metrics.nonblank + ",\n  \"distinctColors\": " + metrics.colors
                + ",\n  \"bounds\": [" + metrics.minX + "," + metrics.minY + "," + metrics.maxX + "," + metrics.maxY + "]"
                + ",\n  \"sha256\": \"" + sha + "\",\n  \"provider\": \"" + VulkanBackendProvider.class.getName() + "\""
                + ",\n  \"device\": \"" + caller.deviceName().replace("\"", "\\\"") + "\""
                + ",\n  \"apiVersion\": " + caller.apiVersion() + ",\n  \"queueFamily\": " + caller.queueFamily()
                + ",\n  \"compiledShaders\": 37,\n  \"catalogPipelines\": " + LuminPipelineCatalog.entries().size()
                + ",\n  \"categories\": {\n" + categories + "  },\n  \"presented\": true,\n  \"cleanup\": true\n}\n";
    }

    private record Variant(byte[] pixels, StandaloneRenderAdapter.Trace trace) { }
    private record Metrics(int nonblank, int colors, int minX, int minY, int maxX, int maxY) { }
}
