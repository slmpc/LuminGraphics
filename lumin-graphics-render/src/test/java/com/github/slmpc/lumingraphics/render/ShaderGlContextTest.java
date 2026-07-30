package com.github.slmpc.lumingraphics.render;

import com.github.slmpc.lumingraphics.render.pipeline.LuminPipelineCatalog;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.BufferUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;

class ShaderGlContextTest {
    @Test
    void compilesAllStagesAndLinksEveryCatalogPipelineInHiddenContext() throws Exception {
        int requestedMajor = Integer.getInteger("lumin.gl.major", 4);
        int requestedMinor = Integer.getInteger("lumin.gl.minor", 1);
        Path root = locateRoot();
        Path sourceRoot = root.resolve("lumin-graphics-render/src/main/resources/assets/lumin_graphics/shaders");
        StringBuilder receipt = new StringBuilder();
        Map<String, Integer> shaders = new HashMap<>();
        int linked = 0;
        long window = 0L;
        GLFWErrorCallback callback = GLFWErrorCallback.create((error, description) ->
                receipt.append("GLFW ERROR ").append(error).append(' ').append(description).append('\n'));
        callback.set();
        try {
            assertTrue(glfwInit(), "GLFW initialization failed");
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, requestedMajor);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, requestedMinor);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
            window = glfwCreateWindow(32, 32, "Lumin shader validation", 0L, 0L);
            assertTrue(window != 0L, "hidden OpenGL context creation failed");
            glfwMakeContextCurrent(window);
            GL.createCapabilities();

            String vendor = glGetString(GL_VENDOR);
            String renderer = glGetString(GL_RENDERER);
            String version = glGetString(GL_VERSION);
            receipt.append("CONTEXT requested=").append(requestedMajor).append('.').append(requestedMinor)
                    .append(" actual=").append(glGetInteger(GL_MAJOR_VERSION)).append('.').append(glGetInteger(GL_MINOR_VERSION))
                    .append(" vendor=").append(vendor).append(" renderer=").append(renderer)
                    .append(" version=").append(version).append('\n');

            try (var paths = Files.walk(sourceRoot)) {
                for (Path source : paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".vsh") || path.toString().endsWith(".fsh")).toList()) {
                    String relative = sourceRoot.relativize(source).toString().replace('\\', '/');
                    int type = relative.endsWith(".vsh") ? GL_VERTEX_SHADER : GL_FRAGMENT_SHADER;
                    int shader = glCreateShader(type);
                    glShaderSource(shader, Files.readString(source, StandardCharsets.UTF_8));
                    glCompileShader(shader);
                    String log = glGetShaderInfoLog(shader);
                    receipt.append("SHADER ").append(relative).append(" status=")
                            .append(glGetShaderi(shader, GL_COMPILE_STATUS)).append(" log=").append(log).append('\n');
                    assertTrue(glGetShaderi(shader, GL_COMPILE_STATUS) == GL_TRUE,
                            () -> relative + " failed: " + log);
                    shaders.put(relative, shader);
                }
            }
            assertTrue(shaders.size() == 37, "expected 37 compiled GL shaders");

            for (var descriptor : LuminPipelineCatalog.entries()) {
                int program = glCreateProgram();
                glAttachShader(program, shaders.get(descriptor.vertex().sourcePath()));
                glAttachShader(program, shaders.get(descriptor.fragment().sourcePath()));
                glLinkProgram(program);
                String log = glGetProgramInfoLog(program);
                receipt.append("PROGRAM ").append(descriptor.id()).append(" status=")
                        .append(glGetProgrami(program, GL_LINK_STATUS)).append(" log=").append(log).append('\n');
                assertTrue(glGetProgrami(program, GL_LINK_STATUS) == GL_TRUE,
                        () -> descriptor.id() + " failed: " + log);
                if (descriptor.id().equals("rectangle")) {
                    int frame = glGetUniformBlockIndex(program, "LuminFrame");
                    int draw = glGetUniformBlockIndex(program, "LuminDraw");
                    int frameBinding = glGetActiveUniformBlocki(program, frame, GL_UNIFORM_BLOCK_BINDING);
                    receipt.append("ABI rectangle LuminFrame.index=").append(frame)
                            .append(" binding=").append(frameBinding)
                            .append(" LuminDraw.index=").append(draw).append('\n');
                    assertTrue(frame != GL_INVALID_INDEX && frameBinding == 0,
                            "LuminFrame must occupy binding 0");
                    assertTrue(draw == GL_INVALID_INDEX,
                            "per-draw ABI must use attributes, not a colliding LuminDraw UBO");
                    verifyRectangleFrameDataAndDraw(program, receipt);
                }
                glDeleteProgram(program);
                linked++;
            }
            receipt.append("PASS shaders=37 programs=").append(linked).append('\n');
        } finally {
            shaders.values().forEach(shader -> glDeleteShader(shader));
            GL.setCapabilities(null);
            if (window != 0L) {
                glfwDestroyWindow(window);
            }
            glfwTerminate();
            callback.free();
            Path evidence = Path.of(System.getProperty("lumin.shader.evidenceDir", root.resolve(".omo/evidence").toString()));
            Files.createDirectories(evidence);
            Files.writeString(evidence.resolve("task-10-gl" + requestedMajor + requestedMinor + ".log"), receipt);
        }
    }

    private static void verifyRectangleFrameDataAndDraw(int program, StringBuilder receipt) {
        int uniformBuffer = glGenBuffers();
        int vertexArray = glGenVertexArrays();
        int vertexBuffer = glGenBuffers();
        try {
            float[] frame = {
                    1, 0, 0, 0,
                    0, 1, 0, 0,
                    0, 0, 1, 0,
                    0, 0, 0, 1,
                    32, 32, 1.0f / 32.0f, 1.0f / 32.0f
            };
            glBindBuffer(GL_UNIFORM_BUFFER, uniformBuffer);
            glBufferData(GL_UNIFORM_BUFFER, frame, GL_STATIC_DRAW);
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, uniformBuffer);

            float[] vertices = {
                    -1, -1, 0, 0, 1, 0, 1,
                     3, -1, 0, 0, 1, 0, 1,
                    -1,  3, 0, 0, 1, 0, 1
            };
            glBindVertexArray(vertexArray);
            glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
            glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 7 * Float.BYTES, 0L);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 4, GL_FLOAT, false, 7 * Float.BYTES, 3L * Float.BYTES);

            glViewport(0, 0, 32, 32);
            glDisable(GL_BLEND);
            glClearColor(0, 0, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            glUseProgram(program);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            glFinish();
            ByteBuffer pixel = BufferUtils.createByteBuffer(4);
            glReadPixels(16, 16, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            int red = Byte.toUnsignedInt(pixel.get(0));
            int green = Byte.toUnsignedInt(pixel.get(1));
            int blue = Byte.toUnsignedInt(pixel.get(2));
            receipt.append("ABI rectangle pixel=").append(red).append(',').append(green).append(',').append(blue)
                    .append(" frameUploadBytes=").append(frame.length * Float.BYTES).append('\n');
            assertTrue(red < 16 && green > 240 && blue < 16,
                    "LuminFrame binding/data or vertex attribute draw failed: " + red + "," + green + "," + blue);
        } finally {
            glUseProgram(0);
            glBindVertexArray(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindBuffer(GL_UNIFORM_BUFFER, 0);
            glDeleteBuffers(vertexBuffer);
            glDeleteVertexArrays(vertexArray);
            glDeleteBuffers(uniformBuffer);
        }
    }

    private static Path locateRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("repository root not found");
        return current;
    }
}
