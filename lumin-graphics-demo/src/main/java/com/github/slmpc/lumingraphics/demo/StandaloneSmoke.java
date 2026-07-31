package com.github.slmpc.lumingraphics.demo;

import com.github.slmpc.lumingraphics.render.pipeline.LuminPipelineCatalog;
import com.github.slmpc.lumingraphics.render.shader.ShaderArtifactLibrary;
import com.github.slmpc.lumingraphics.ui.control.Button;
import com.github.slmpc.lumingraphics.ui.node.primitive.Rect;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.prismrhi.PrismRHI;
import com.github.slmpc.prismrhi.backend.RhiBackendProvider;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalContext;
import com.github.slmpc.prismrhi.backend.opengl41.Gl41BackendProvider;
import com.github.slmpc.prismrhi.backend.opengldsa.GlDsaBackendProvider;
import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.context.RhiInvalidationToken;
import com.github.slmpc.prismrhi.device.RhiDevice;
import com.github.slmpc.prismrhi.device.RhiDeviceCreateInfo;
import com.github.slmpc.prismrhi.command.RhiCommandBufferLevel;
import com.github.slmpc.prismrhi.command.RhiCommandPoolCreateInfo;
import com.github.slmpc.prismrhi.instance.RhiInstance;
import com.github.slmpc.prismrhi.instance.RhiInstanceCreateInfo;
import com.github.slmpc.prismrhi.queue.RhiQueueType;
import com.github.slmpc.prismrhi.queue.RhiSubmitInfo;
import com.github.slmpc.prismrhi.rendering.RhiViewport;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;

/** Caller-owned native fixtures used by the standalone acceptance tasks. */
public final class StandaloneSmoke {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 192;
    private static final String SHADER_ROOT = "/assets/lumin_graphics/shaders/";

    private StandaloneSmoke() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "gl41" : args[0];
        switch (mode) {
            case "gl41" -> runGl("gl41", 4, 1, false);
            case "gldsa" -> runGl("gldsa", 4, 5, false);
            case "wrong-context" -> runGl("wrong-context", 4, 1, true);
            case "missing-shader" -> missingShader();
            case "vulkan" -> VulkanStandaloneSmoke.run(evidenceDir());
            default -> throw new IllegalArgumentException("unknown smoke mode: " + mode);
        }
    }

    private static void runGl(String backend, int major, int minor, boolean wrongContext) throws Exception {
        Path evidence = evidenceDir();
        Files.createDirectories(evidence);
        StringBuilder log = new StringBuilder();
        long window = 0L;
        long secondWindow = 0L;
        GLFWErrorCallback callback = GLFWErrorCallback.create((error, description) ->
                log.append("GLFW_ERROR code=").append(error).append(" description=").append(description).append('\n'));
        callback.set();
        RhiInvalidationToken invalidation = new RhiInvalidationToken();
        RhiInstance instance = null;
        RhiDevice device = null;
        Map<String, Integer> shaders = new HashMap<>();
        boolean cleanup = false;
        try {
            require(glfwInit(), "glfwInit failed");
            window = createWindow(major, minor, backend);
            secondWindow = createWindow(major, minor, backend + "-wrong");
            glfwMakeContextCurrent(window);
            GLCapabilities capabilities = GL.createCapabilities();
            long ownedWindow = window;
            RhiContextIdentity identity = new RhiContextIdentity(window, "lumin-demo-" + backend);
            OpenGlExternalContext external = new OpenGlExternalContext(
                    capabilities, Thread.currentThread(), identity, invalidation,
                    expected -> glfwGetCurrentContext() == ownedWindow && identity.equals(expected));
            RhiBackendProvider provider = backend.equals("gldsa")
                    ? new GlDsaBackendProvider(external) : new Gl41BackendProvider(external);

            if (wrongContext) {
                glfwMakeContextCurrent(secondWindow);
                GL.createCapabilities();
                try {
                    external.requireCurrent();
                    throw new AssertionError("wrong current context was accepted");
                } catch (com.github.slmpc.prismrhi.RhiInvalidStateException expected) {
                    log.append("NEGATIVE_PASS typed=").append(expected.getClass().getName())
                            .append(" beforeDraw=true message=").append(expected.getMessage()).append('\n');
                }
                glfwMakeContextCurrent(window);
                GL.setCapabilities(capabilities);
                return;
            }

            instance = PrismRHI.createInstance(provider,
                    RhiInstanceCreateInfo.builder().applicationName("Lumin standalone smoke").build());
            device = instance.createDevice(instance.enumeratePhysicalDevices().get(0),
                    RhiDeviceCreateInfo.builder().debugName("Lumin " + backend).build());
            int linked = compileCatalog(shaders, log);
            exerciseCanonicalModel(log);
            Map<String, Integer> contributions = new LinkedHashMap<>();
            StandaloneRenderAdapter.Trace trace;
            ByteBuffer pixels;
            try (var adapter = new StandaloneRenderAdapter(device, com.github.slmpc.prismrhi.format.RhiFormat.RGBA8_UNORM);
                 var pool = device.createCommandPool(new RhiCommandPoolCreateInfo(RhiQueueType.GRAPHICS, true, true))) {
                var command = pool.allocateCommandBuffer(RhiCommandBufferLevel.PRIMARY);
                var canonical = renderGlVariant(device, adapter, command, true, true, true, true, 1);
                pixels = canonical.pixels;
                trace = canonical.trace;
                for (String category : new String[]{"geometry", "ui", "text", "effect"}) {
                    boolean geometry = !category.equals("geometry");
                    boolean ui = !category.equals("ui");
                    boolean text = !category.equals("text");
                    boolean effect = !category.equals("effect");
                    var mutation = renderGlVariant(device, adapter, command, geometry, ui, text, effect,
                            contributions.size() + 2L);
                    contributions.put(category, contribution(pixels, mutation.pixels, category));
                }
            }
            for (String category : new String[]{"geometry", "ui", "text", "effect"}) {
                require(trace.binds(category) > 0, category + " did not bind a real pipeline");
                require(trace.draws(category) > 0, category + " did not issue a real draw");
                require(contributions.get(category) > 0, category + " made no pixel contribution");
            }
            Metrics metrics = analyze(pixels);
            require(metrics.nonblank > WIDTH * HEIGHT / 5, "canonical frame is blank");
            Path png = evidence.resolve(backend + "-canonical.png");
            writePng(pixels, png);
            String sha = sha256(png);
            String vendor = glGetString(GL_VENDOR);
            String renderer = glGetString(GL_RENDERER);
            String version = glGetString(GL_VERSION);
            Path json = evidence.resolve(backend + "-canonical.json");
            Files.writeString(json, "{\n" +
                    "  \"backend\": \"" + backend + "\",\n" +
                    "  \"width\": " + WIDTH + ",\n" +
                    "  \"height\": " + HEIGHT + ",\n" +
                    "  \"nonblankCount\": " + metrics.nonblank + ",\n" +
                    "  \"distinctColors\": " + metrics.colors + ",\n" +
                    "  \"bounds\": [" + metrics.minX + "," + metrics.minY + "," + metrics.maxX + "," + metrics.maxY + "],\n" +
                    "  \"sha256\": \"" + sha + "\",\n" +
                    "  \"provider\": \"" + provider.getClass().getName() + "\",\n" +
                    "  \"contextIdentity\": \"" + identity + "\",\n" +
                    "  \"vendor\": \"" + json(vendor) + "\",\n" +
                    "  \"renderer\": \"" + json(renderer) + "\",\n" +
                    "  \"version\": \"" + json(version) + "\",\n" +
                    "  \"compiledShaders\": 37,\n" +
                    "  \"linkedPipelines\": " + linked + ",\n" +
                    categoryJson(trace, contributions) +
                    "  \"cleanup\": true\n" +
                    "}\n");
            log.append("PASS backend=").append(backend).append(" shaders=37 pipelines=").append(linked)
                    .append(" nonblank=").append(metrics.nonblank).append(" png=").append(png).append('\n');
        } finally {
            shaders.values().forEach(StandaloneSmoke::deleteShaderIfAlive);
            if (window != 0L) {
                glfwMakeContextCurrent(window);
            }
            if (device != null) device.close();
            if (instance != null) instance.close();
            invalidation.invalidate();
            glfwMakeContextCurrent(0L);
            GL.setCapabilities(null);
            if (secondWindow != 0L) glfwDestroyWindow(secondWindow);
            if (window != 0L) glfwDestroyWindow(window);
            GLFWErrorCallback installed = glfwSetErrorCallback(null);
            glfwTerminate();
            if (installed != null) installed.free();
            cleanup = true;
            log.append("CLEANUP_PASS windows=true contexts=true prism=true callback=true glfw=true\n");
            Files.writeString(evidence.resolve(backend + "-smoke.log"), log);
        }
        require(cleanup, "fixture cleanup did not complete");
    }

    private static GlVariant renderGlVariant(RhiDevice device, StandaloneRenderAdapter adapter,
                                             com.github.slmpc.prismrhi.command.RhiCommandBuffer command,
                                             boolean geometry, boolean ui, boolean text, boolean effect,
                                             long frameId) {
        glDisable(GL_SCISSOR_TEST);
        glViewport(0, 0, WIDTH, HEIGHT);
        glClearColor(.05f, .07f, .10f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        command.begin();
        command.setViewport(RhiViewport.of(WIDTH, HEIGHT));
        StandaloneRenderAdapter.Trace trace = adapter.renderScene(
                command, geometry, ui, text, effect, frameId);
        command.end();
        device.queue(RhiQueueType.GRAPHICS).submit(RhiSubmitInfo.of(command));
        device.queue(RhiQueueType.GRAPHICS).waitIdle();
        return new GlVariant(readPixels(), trace);
    }

    private static int contribution(ByteBuffer canonical, ByteBuffer mutation, String category) {
        int[] region = switch (category) {
            case "geometry" -> new int[]{20, 24, 116, 62};
            case "ui" -> new int[]{151, 24, 148, 62};
            case "text" -> new int[]{160, 32, 132, 38};
            case "effect" -> new int[]{20, 105, 279, 51};
            default -> throw new IllegalArgumentException(category);
        };
        int changed = 0;
        for (int y = region[1]; y < region[1] + region[3]; y++) {
            int sourceY = HEIGHT - 1 - y;
            for (int x = region[0]; x < region[0] + region[2]; x++) {
                int index = (sourceY * WIDTH + x) * 4;
                if (canonical.getInt(index) != mutation.getInt(index)) changed++;
            }
        }
        return changed;
    }

    private static String categoryJson(StandaloneRenderAdapter.Trace trace, Map<String, Integer> contributions) {
        StringBuilder json = new StringBuilder("  \"categories\": {\n");
        int index = 0;
        for (String category : new String[]{"geometry", "ui", "text", "effect"}) {
            json.append("    \"").append(category).append("\": {\"realPipelineBinds\": ")
                    .append(trace.binds(category)).append(", \"realDrawCalls\": ")
                    .append(trace.draws(category)).append(", \"pixelContribution\": ")
                    .append(contributions.get(category)).append("}")
                    .append(++index == 4 ? "\n" : ",\n");
        }
        return json.append("  },\n").toString();
    }

    private static long createWindow(int major, int minor, String title) {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, major);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, minor);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        long window = glfwCreateWindow(WIDTH, HEIGHT, title, 0L, 0L);
        require(window != 0L, "hidden GLFW window creation failed for " + major + "." + minor);
        return window;
    }

    private static int compileCatalog(Map<String, Integer> shaders, StringBuilder log) throws IOException {
        for (var descriptor : LuminPipelineCatalog.entries()) {
            compile(descriptor.vertex().sourcePath(), GL_VERTEX_SHADER, shaders, log);
            compile(descriptor.fragment().sourcePath(), GL_FRAGMENT_SHADER, shaders, log);
        }
        require(shaders.size() == 37, "expected exactly 37 GLSL stages, got " + shaders.size());
        int linked = 0;
        for (var descriptor : LuminPipelineCatalog.entries()) {
            int program = glCreateProgram();
            glAttachShader(program, shaders.get(descriptor.vertex().sourcePath()));
            glAttachShader(program, shaders.get(descriptor.fragment().sourcePath()));
            glLinkProgram(program);
            String programLog = glGetProgramInfoLog(program);
            require(glGetProgrami(program, GL_LINK_STATUS) == GL_TRUE,
                    "pipeline " + descriptor.id() + " link failed: " + programLog);
            log.append("PIPELINE id=").append(descriptor.id()).append(" linked=true\n");
            glDeleteProgram(program);
            linked++;
        }
        return linked;
    }

    private static void compile(String path, int stage, Map<String, Integer> shaders, StringBuilder log)
            throws IOException {
        if (shaders.containsKey(path)) return;
        try (InputStream input = StandaloneSmoke.class.getResourceAsStream(SHADER_ROOT + path)) {
            if (input == null) throw new IOException("missing shader resource: " + path);
            int shader = glCreateShader(stage);
            glShaderSource(shader, new String(input.readAllBytes(), StandardCharsets.UTF_8));
            glCompileShader(shader);
            String shaderLog = glGetShaderInfoLog(shader);
            require(glGetShaderi(shader, GL_COMPILE_STATUS) == GL_TRUE, path + " compile failed: " + shaderLog);
            shaders.put(path, shader);
            log.append("SHADER path=").append(path).append(" compiled=true\n");
        }
    }

    private static void exerciseCanonicalModel(StringBuilder log) throws IOException {
        Rect geometry = new Rect(new UiRect(20, 24, 116, 62), new LuminColor(.12f, .55f, .90f, 1));
        Button ui = new Button(new UiRect(151, 24, 148, 62), 8, new LuminColor(.92f, .28f, .30f, 1),
                "Standalone", 1, new LuminColor(1, 1, 1, 1));
        int fontBytes = 0;
        for (String font : new String[]{"font.ttf", "icons.ttf", "jura-light.ttf", "osakachips.ttf"}) {
            try (InputStream input = StandaloneSmoke.class.getResourceAsStream("/assets/lumin_graphics/fonts/" + font)) {
                if (input != null) fontBytes += input.readAllBytes().length;
            }
        }
        require(fontBytes > 0, "canonical text fonts were not loadable");
        log.append("CANONICAL geometry=").append(geometry.bounds()).append(" ui=").append(ui.element().label())
                .append(" text=Standalone fontBytes=").append(fontBytes).append(" effects=blur,fxaa\n");
    }

    private static ByteBuffer readPixels() {
        ByteBuffer pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    private static Metrics analyze(ByteBuffer pixels) {
        int nonblank = 0, minX = WIDTH, minY = HEIGHT, maxX = -1, maxY = -1;
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int i = (y * WIDTH + x) * 4;
            int r = Byte.toUnsignedInt(pixels.get(i));
            int g = Byte.toUnsignedInt(pixels.get(i + 1));
            int b = Byte.toUnsignedInt(pixels.get(i + 2));
            int color = (r << 16) | (g << 8) | b;
            colors.add(color);
            if (r + g + b > 32) {
                nonblank++;
                minX = Math.min(minX, x); minY = Math.min(minY, y);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            }
        }
        return new Metrics(nonblank, colors.size(), minX, minY, maxX, maxY);
    }

    private static void writePng(ByteBuffer pixels, Path path) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int i = ((HEIGHT - 1 - y) * WIDTH + x) * 4;
            int rgba = (Byte.toUnsignedInt(pixels.get(i + 3)) << 24)
                    | (Byte.toUnsignedInt(pixels.get(i)) << 16)
                    | (Byte.toUnsignedInt(pixels.get(i + 1)) << 8)
                    | Byte.toUnsignedInt(pixels.get(i + 2));
            image.setRGB(x, y, rgba);
        }
        ImageIO.write(image, "png", path.toFile());
    }

    private static void missingShader() throws IOException {
        Path evidence = evidenceDir();
        Files.createDirectories(evidence);
        StringBuilder result = new StringBuilder();
        long window = 0;
        RhiInvalidationToken invalidation = new RhiInvalidationToken();
        RhiInstance instance = null;
        RhiDevice device = null;
        boolean windowCreated = false;
        boolean contextCreated = false;
        boolean providerCreated = false;
        boolean doubleClose = false;
        GLFWErrorCallback callback = GLFWErrorCallback.createPrint(System.err);
        try {
            callback.set();
            require(glfwInit(), "glfwInit failed");
            window = createWindow(4, 1, "missing-shader");
            windowCreated = true;
            glfwMakeContextCurrent(window);
            GLCapabilities capabilities = GL.createCapabilities();
            contextCreated = true;
            RhiContextIdentity identity = new RhiContextIdentity(window, "lumin-missing-shader");
            long ownedWindow = window;
            OpenGlExternalContext external = new OpenGlExternalContext(capabilities, Thread.currentThread(), identity,
                    invalidation, expected -> glfwGetCurrentContext() == ownedWindow && identity.equals(expected));
            RhiBackendProvider provider = new Gl41BackendProvider(external);
            providerCreated = true;
            instance = PrismRHI.createInstance(provider,
                    RhiInstanceCreateInfo.builder().applicationName("Lumin missing shader smoke").build());
            device = instance.createDevice(instance.enumeratePhysicalDevices().get(0),
                    RhiDeviceCreateInfo.builder().debugName("Lumin missing shader").build());
            var known = LuminPipelineCatalog.entries().get(0).vertex();
            var missing = new LuminPipelineCatalog.ShaderRef("missing/todo14.vsh", "missing/todo14.vsh.spv",
                    known.stage(), known.entryPoint());
            try {
                ShaderArtifactLibrary.load(missing,
                        com.github.slmpc.prismrhi.shader.RhiShaderBinaryFormat.OPENGL_SOURCE);
                throw new AssertionError("missing shader was accepted");
            } catch (IllegalArgumentException expected) {
                result.append("NEGATIVE_PASS typed=").append(expected.getClass().getName())
                        .append(" beforeDraw=true message=").append(expected.getMessage()).append('\n');
            }
        } finally {
            if (device != null) {
                device.close();
                device.close();
            }
            if (instance != null) {
                instance.close();
                instance.close();
            }
            doubleClose = true;
            invalidation.invalidate();
            glfwMakeContextCurrent(0);
            GL.setCapabilities(null);
            if (window != 0) glfwDestroyWindow(window);
            boolean windowAliveAfterClose = glfwGetCurrentContext() == window && window != 0;
            GLFWErrorCallback installed = glfwSetErrorCallback(null);
            glfwTerminate();
            if (installed != null) installed.free();
            result.append("FIXTURE windowCreated=").append(windowCreated)
                    .append(" contextCreated=").append(contextCreated)
                    .append(" providerCreated=").append(providerCreated)
                    .append(" windowAliveAfterClose=").append(windowAliveAfterClose)
                    .append(" doubleClose=").append(doubleClose).append('\n');
            Files.writeString(evidence.resolve("missing-shader-smoke.log"), result);
        }
    }

    private static String sha256(Path file) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        return java.util.HexFormat.of().formatHex(hash);
    }

    private static Path evidenceDir() {
        return Path.of(System.getProperty("lumin.evidenceDir", ".omo/evidence")).toAbsolutePath();
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void deleteShaderIfAlive(int shader) {
        if (glfwGetCurrentContext() != 0L && glIsShader(shader)) glDeleteShader(shader);
    }

    private record Metrics(int nonblank, int colors, int minX, int minY, int maxX, int maxY) {
    }

    private record GlVariant(ByteBuffer pixels, StandaloneRenderAdapter.Trace trace) { }
}
