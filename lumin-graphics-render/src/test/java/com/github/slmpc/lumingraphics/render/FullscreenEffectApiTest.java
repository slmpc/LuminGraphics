package com.github.slmpc.lumingraphics.render;

import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.lumingraphics.render.shader.BlurShader;
import com.github.slmpc.lumingraphics.render.shader.FilterShader;
import com.github.slmpc.lumingraphics.render.shader.FxaaShader;
import com.github.slmpc.lumingraphics.render.shader.GlslSandbox;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FullscreenEffectApiTest {
    @Test
    void suppliedInputAndDynamicUniformBytesReachOneBalancedEffectPass() {
        FakeRhi fake = new FakeRhi();
        ByteBuffer uniforms = ByteBuffer.allocateDirect(16).order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(7.5f).putFloat(64).putFloat(32).putFloat(0);
        uniforms.flip();

        try (BlurShader blur = new BlurShader(fake.resources(), 256)) {
            blur.apply(fake.execution(2, 1, 64, 32),
                    new Render2DTexture.Resource("scene-color"), uniforms);
        }

        assertEquals(1, fake.trace().stream().filter("effectInput=scene-color"::equals).count());
        assertEquals(1, fake.trace().stream().filter("effectUniformBytes=16"::equals).count());
        assertTrue(fake.trace().contains("effectUniformHex=0000f040000080420000004200000000"));
        assertEquals(1, fake.trace().stream().filter("effectDescriptor=blur"::equals).count());
        assertEquals(1, fake.trace().stream().filter("render.begin=64x32"::equals).count());
        assertEquals(1, fake.trace().stream().filter("render.end"::equals).count());
        assertEquals(1, fake.trace().stream().filter("draw=3"::equals).count());
    }

    @Test
    void everyConcreteEffectUsesItsOwnDescriptorIdAndUniformPayload() {
        FakeRhi fake = new FakeRhi();
        var execution = fake.execution(3, 2, 80, 40);
        Render2DTexture input = new Render2DTexture.Resource("input");
        ByteBuffer bytes = ByteBuffer.allocateDirect(4).putInt(0, 0x12345678);

        try (FxaaShader fxaa = new FxaaShader(fake.resources(), 256);
             FilterShader filter = new FilterShader(fake.resources(), 256)) {
            fxaa.apply(execution, input, bytes);
            filter.apply(execution, input, bytes);
        }

        assertTrue(fake.trace().contains("effectDescriptor=fxaa"));
        assertTrue(fake.trace().contains("effectDescriptor=filter"));
        assertEquals(2, fake.trace().stream().filter("effectUniformBytes=4"::equals).count());
    }

    @Test
    void sandboxRejectsDescriptorlessAndMissingInputApplication() {
        FakeRhi fake = new FakeRhi();
        try (GlslSandbox sandbox = new GlslSandbox(fake.resources(), 256, "menu-clouds")) {
            assertThrows(IllegalArgumentException.class,
                    () -> sandbox.apply(fake.execution(4, 3, 16, 16)));
            assertThrows(NullPointerException.class,
                    () -> sandbox.apply(fake.execution(4, 3, 16, 16), null, ByteBuffer.allocate(16)));
        }
    }

    @Test
    void effectEndsPassAndFrameAfterDrawFailureAndCloseIsRepeatable() {
        FakeRhi fake = new FakeRhi();
        BlurShader blur = new BlurShader(fake.resources(), 256);
        fake.failNextDraw();
        assertThrows(IllegalStateException.class, () -> blur.apply(fake.execution(5, 4, 20, 10),
                new Render2DTexture.Resource("scene-color"), ByteBuffer.allocate(16)));
        blur.close();
        blur.close();

        assertEquals(1, fake.trace().stream().filter("render.begin=20x10"::equals).count());
        assertEquals(1, fake.trace().stream().filter("render.end"::equals).count());
        assertEquals(3, fake.closedBuffers());
    }

    @Test
    void missingInputDescriptorAndReuseAfterCloseFailBeforeDrawing() {
        FakeRhi fake = new FakeRhi();
        BlurShader blur = new BlurShader(fake.resources(), 256);
        assertThrows(IllegalStateException.class, () -> blur.apply(fake.execution(6, 5, 20, 10),
                new Render2DTexture.Resource("missing"), ByteBuffer.allocate(16)));
        blur.close();
        assertThrows(RuntimeException.class, () -> blur.apply(fake.execution(7, 6, 20, 10),
                new Render2DTexture.Resource("scene-color"), ByteBuffer.allocate(16)));
        assertEquals(0, fake.trace().stream().filter("draw=3"::equals).count());
    }

    @Test
    void manualEffectTraceScenario() throws IOException {
        FakeRhi fake = new FakeRhi();
        Render2DTexture input = new Render2DTexture.Resource("manual-scene");
        try (FilterShader filter = new FilterShader(fake.resources(), 256)) {
            filter.apply(fake.execution(8, 7, 48, 24), input,
                    ByteBuffer.allocateDirect(16).order(ByteOrder.LITTLE_ENDIAN)
                            .putFloat(1).putFloat(.5f).putFloat(.25f).putFloat(.75f).flip());
        }

        String sandboxFailure;
        try (GlslSandbox sandbox = new GlslSandbox(fake.resources(), 256, "menu-clouds")) {
            sandboxFailure = assertThrows(IllegalArgumentException.class,
                    () -> sandbox.apply(fake.execution(9, 8, 48, 24))).getMessage();
        }
        String missingFailure;
        try (BlurShader blur = new BlurShader(fake.resources(), 256)) {
            missingFailure = assertThrows(IllegalStateException.class,
                    () -> blur.apply(fake.execution(10, 9, 48, 24),
                            new Render2DTexture.Resource("missing"), ByteBuffer.allocate(16))).getMessage();
        }

        Path tracePath = Path.of(System.getenv().getOrDefault("LUMIN_EFFECT_TRACE",
                "build/evidence/fullscreen-effect-rhi-trace.json"));
        Path parent = tracePath.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(tracePath, jsonTrace(fake.trace(), sandboxFailure, missingFailure));
    }

    private static String jsonTrace(List<String> trace, String sandboxFailure, String missingFailure) {
        String events = trace.stream().map(FullscreenEffectApiTest::jsonString)
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"scenario\":\"manualEffectTraceScenario\",\"events\":[" + events
                + "],\"sandboxFailure\":" + jsonString(sandboxFailure)
                + ",\"missingInputFailure\":" + jsonString(missingFailure) + "}\n";
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
