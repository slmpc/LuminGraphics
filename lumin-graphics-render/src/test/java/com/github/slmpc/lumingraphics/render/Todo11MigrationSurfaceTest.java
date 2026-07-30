package com.github.slmpc.lumingraphics.render;

import com.github.slmpc.lumingraphics.core.RenderContext;
import com.github.slmpc.lumingraphics.core.buffer.BufferWriter;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.lumingraphics.core.vertex.LuminVertexFormats;
import com.github.slmpc.lumingraphics.render.immediate.LuminImmediateRenderer;
import com.github.slmpc.lumingraphics.render.immediate.LuminTessellator;
import com.github.slmpc.lumingraphics.render.pipeline.LuminPipelineCatalog;
import com.github.slmpc.lumingraphics.render.pipeline.LuminRenderPipelines;
import com.github.slmpc.lumingraphics.render.renderer.RendererSet;
import com.github.slmpc.lumingraphics.render.scheduler.GlyphQuad;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DBounds;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommandKind;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScheduler;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScissor;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.lumingraphics.render.scheduler.Render3DScheduler;
import com.github.slmpc.lumingraphics.render.shader.BlurShader;
import com.github.slmpc.lumingraphics.render.shader.FilterShader;
import com.github.slmpc.lumingraphics.render.shader.FxaaShader;
import com.github.slmpc.lumingraphics.render.shader.GlslSandbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Todo11MigrationSurfaceTest {
    private static final LuminColor WHITE = new LuminColor(1, 1, 1, 1);

    @Test @DisplayName("MIG-RENDER-PIPELINES")
    void pipelineIdsResolveToTheCatalogAbi() {
        assertEquals("rectangle.vsh", LuminRenderPipelines.require(LuminRenderPipelines.RECTANGLE).vertex().sourcePath());
        assertEquals(56, LuminPipelineCatalog.require("texture").vertexLayout().schema().stride());
    }

    @Test @DisplayName("MIG-INJECTED-RENDER-CONTEXT")
    void renderContextRejectsInvalidFrameStateAndFramePairsCommands() {
        FakeRhi fake = new FakeRhi();
        assertThrows(RuntimeException.class, () -> new RenderContext(fake.device(), fake.execution(1, 0, 2, 2).commands(), 1, 1, 2, 2));
        try (RenderFrame frame = new RenderFrame(fake.execution(1, 0, 2, 2).commands(), fake.resources(), 1, 0, 2, 2)) {
            assertEquals(1, frame.execution().frameId());
        }
        assertTrue(fake.trace().containsAll(List.of("command.begin", "command.end")));
    }

    @Test @DisplayName("MIG-INJECTED-TEXTURE-RESOLVER")
    void missingTextureFailsBeforeDraw() {
        Fixture f = fixture();
        f.scheduler.layer(0).addTexture(bounds(), new Render2DTexture.Resource("missing"), WHITE);
        assertThrows(IllegalStateException.class, () -> f.scheduler.flush(f.execution()));
        assertTrue(f.fake.trace().stream().noneMatch(value -> value.startsWith("draw=")));
        f.scheduler.close();
    }

    @Test @DisplayName("MIG-CORE-VERTEX-FORMATS")
    void vertexFormatsMatchWrittenRendererStrides() {
        assertEquals(List.of(48, 52, 56), List.of(LuminVertexFormats.ROUND_RECT.stride(),
                LuminVertexFormats.ROUND_RECT_OUTLINE.stride(), LuminVertexFormats.TEXTURE.stride()));
    }

    @Test @DisplayName("MIG-CORE-BUFFER-WRITES")
    void bufferWriterIsLittleEndianAndBoundsChecked() {
        BufferWriter writer = new BufferWriter(8).putInt(0x01020304).putFloat(1);
        assertEquals(ByteOrder.LITTLE_ENDIAN, writer.bytes().order());
        assertThrows(RuntimeException.class, () -> writer.putInt(2));
    }

    @Test @DisplayName("MIG-CORE-RING-BUFFER")
    void ringBehaviorIsCoveredByNoOverwriteScenario() {
        Todo11BehaviorTest suite = new Todo11BehaviorTest();
        suite.ringRotatesWithoutOverwritingInFlightFramesAndRejectsOverflow();
    }

    @Test @DisplayName("MIG-RENDER-IMMEDIATE")
    void immediateRendererBindsPipelineBufferAndDraw() {
        FakeRhi fake = new FakeRhi();
        LuminImmediateRenderer renderer = new LuminImmediateRenderer(fake.resources(), 256);
        var execution = fake.execution(1, 0, 10, 10);
        renderer.beginFrame(execution);
        renderer.draw(new LuminTessellator(3).vertex(0, 0, 0, WHITE).vertex(1, 0, 0, WHITE)
                .vertex(0, 1, 0, WHITE).build(), "triangle", null, execution);
        renderer.endFrame(); renderer.close();
        assertEquals(List.of("triangle"), fake.boundPipelines());
        assertTrue(fake.trace().contains("draw=3"));
    }

    @Test @DisplayName("MIG-RENDER-TESSELLATOR")
    void tessellatorExpandsQuadToSixExactVertices() {
        var batch = new LuminTessellator(6).quad(1, 2, 3, 4, WHITE).build();
        assertEquals(6, batch.vertexCount()); assertEquals(96, batch.bytes().remaining());
    }

    @Test @DisplayName("MIG-RENDER-LIFECYCLE")
    void rendererLifecycleRejectsCloseDuringFrame() {
        FakeRhi fake = new FakeRhi();
        RendererSet set = RendererSet.create(fake.resources(), 512);
        set.beginFrame(fake.execution(1, 0, 10, 10));
        assertThrows(IllegalStateException.class, set::close);
        set.endFrame(); set.close(); assertTrue(set.allFramesEnded());
    }

    @Test @DisplayName("MIG-RECT-BATCHING") void adjacentRectsShareOneUploadAndDraw() {
        Fixture f = fixture();
        f.scheduler.layer(0).addRect(bounds(), WHITE);
        f.scheduler.layer(0).addRect(new Render2DBounds(6, 1, 4, 4), WHITE);
        f.scheduler.flush(f.execution());
        assertEquals(List.of("rectangle"), f.fake.boundPipelines());
        assertEquals(1, f.fake.trace().stream().filter(value -> value.equals("draw=12")).count());
        f.scheduler.close();
    }
    @Test @DisplayName("MIG-ROUND-OUTLINE") void outlineUsesExactPipeline() { assertCommand("round-rectangle-outline", f -> f.scheduler.layer(0).addOutline(bounds(), 2, 1, WHITE)); }
    @Test @DisplayName("MIG-ROUND-RECT") void roundRectUsesExactPipeline() { assertCommand("round-rectangle", f -> f.scheduler.layer(0).addRoundRect(bounds(), 2, WHITE)); }
    @Test @DisplayName("MIG-SHADOW-BATCHING") void shadowUsesExpandedBoundsAndPipeline() { assertCommand("shadow", f -> f.scheduler.layer(0).addShadow(bounds(), 2, 3, WHITE)); }

    @Test @DisplayName("MIG-GLYPH-BATCHING")
    void glyphSinkBatchesAtlasQuadsWithoutTextTypes() {
        assertCommand("ttf-font-aa", f -> f.scheduler.layer(0).addGlyphs(bounds(),
                new Render2DTexture.Resource("font-atlas"), List.of(new GlyphQuad(bounds(), 0, 0, .5f, .5f, WHITE))));
    }

    @Test @DisplayName("MIG-TEXTURE-BATCHING")
    void textureBindsItsInjectedDescriptor() {
        Fixture f = fixture(); f.scheduler.layer(0).addTexture(bounds(), new Render2DTexture.Resource("atlas"), WHITE);
        f.scheduler.flush(f.execution()); assertTrue(f.fake.trace().contains("descriptor=atlas")); f.scheduler.close();
    }

    @Test @DisplayName("MIG-TRIANGLE-BATCHING") void triangleUsesThreeVertices() {
        Fixture f = fixture(); f.scheduler.layer(0).addTriangle(5, 5, 2, WHITE); f.scheduler.flush(f.execution());
        assertTrue(f.fake.trace().contains("draw=3")); f.scheduler.close();
    }

    @Test @DisplayName("MIG-SCHEDULER-BOUNDS") void boundsUseStrictIntersection() {
        assertTrue(new Render2DBounds(0, 0, 2, 2).intersects(new Render2DBounds(1, 1, 2, 2)));
        assertTrue(!new Render2DBounds(0, 0, 1, 1).intersects(new Render2DBounds(1, 0, 1, 1)));
    }
    @Test @DisplayName("MIG-SCHEDULER-COMMAND") void commandRecordsCaptureSubmissionScissor() {
        Fixture f = fixture(); var layer = f.scheduler.layer(0); layer.pushScissor(new Render2DScissor(1, 2, 3, 4));
        layer.addRect(bounds(), WHITE); layer.popScissor(); f.scheduler.flush(f.execution());
        assertTrue(f.fake.trace().contains("scissor=1,2,3,4")); f.scheduler.close();
    }
    @Test @DisplayName("MIG-SCHEDULER-KINDS") void commandKindsCoverAllRendererRoutes() {
        assertEquals(7, Render2DCommandKind.values().length);
        assertEquals(List.of("SHADOW", "ROUND_RECT", "ROUND_RECT_OUTLINE", "RECT", "TRIANGLE", "TEXTURE", "GLYPH"),
                java.util.Arrays.stream(Render2DCommandKind.values()).map(Enum::name).toList());
    }
    @Test @DisplayName("MIG-SCHEDULER-ORDER") void schedulerOrderIsCoveredByGoldenTrace() {
        new Todo11BehaviorTest().schedulerSortsLayersStablyCullsAndIntersectsNestedScissors();
    }
    @Test @DisplayName("MIG-SCHEDULER-SCISSOR") void invertedAndDisjointScissorsFail() {
        assertThrows(IllegalArgumentException.class, () -> new Render2DScissor(1, 1, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> new Render2DScissor(0, 0, 2, 2).intersect(new Render2DScissor(4, 4, 1, 1)));
    }
    @Test @DisplayName("MIG-SCHEDULER-TEXTURE") void textureReferencesValidateIds() {
        assertThrows(IllegalArgumentException.class, () -> new Render2DTexture.Resource(" "));
        assertEquals("atlas", new Render2DTexture.Resource("atlas").id());
    }
    @Test @DisplayName("MIG-SCHEDULER-3D") void scheduler3dPriorityHasStableGoldenOrder() {
        new Todo11BehaviorTest().threeDimensionalCommandsUsePriorityThenStableInsertionOrder();
    }

    @Test @DisplayName("MIG-SHADER-BLUR") void blurBindsInputAndPipeline() { assertEffect("blur", (f, e) -> { try (var s = new BlurShader(f.resources(), 256)) { s.apply(e, new Render2DTexture.Resource("input")); }}); }
    @Test @DisplayName("MIG-SHADER-FILTER") void filterBindsInputAndPipeline() { assertEffect("filter", (f, e) -> { try (var s = new FilterShader(f.resources(), 256)) { s.apply(e, new Render2DTexture.Resource("input")); }}); }
    @Test @DisplayName("MIG-SHADER-FXAA") void fxaaBindsInputAndPipeline() { assertEffect("fxaa", (f, e) -> { try (var s = new FxaaShader(f.resources(), 256)) { s.apply(e, new Render2DTexture.Resource("input")); }}); }
    @Test @DisplayName("MIG-SHADER-SANDBOX") void sandboxRejectsNonSandboxPipelineAndDrawsMenu() {
        FakeRhi fake = new FakeRhi(); assertThrows(IllegalArgumentException.class, () -> new GlslSandbox(fake.resources(), 256, "rectangle"));
        try (var shader = new GlslSandbox(fake.resources(), 256, "menu-clouds")) { shader.apply(fake.execution(1, 0, 8, 8)); }
        assertEquals(List.of("menu-clouds"), fake.boundPipelines());
    }

    @Test void missingShaderFailsBeforeBindingOrDraw() {
        Fixture f = fixture(); f.fake.missingPipeline("rectangle"); f.scheduler.layer(0).addRect(bounds(), WHITE);
        assertThrows(IllegalStateException.class, () -> f.scheduler.flush(f.execution()));
        assertTrue(f.fake.trace().stream().noneMatch(value -> value.startsWith("draw="))); f.scheduler.close();
    }

    private static void assertCommand(String pipeline, java.util.function.Consumer<Fixture> submit) {
        Fixture f = fixture(); submit.accept(f); f.scheduler.flush(f.execution());
        assertEquals(List.of(pipeline), f.fake.boundPipelines()); f.scheduler.close();
    }
    private static void assertEffect(String pipeline, EffectCall call) {
        FakeRhi fake = new FakeRhi(); call.run(fake, fake.execution(1, 0, 8, 8));
        assertEquals(List.of(pipeline), fake.boundPipelines()); assertTrue(fake.trace().contains("descriptor=input"));
    }
    private static Fixture fixture() {
        FakeRhi fake = new FakeRhi(); return new Fixture(fake, new Render2DScheduler(RendererSet.create(fake.resources(), 4096), 2));
    }
    private static Render2DBounds bounds() { return new Render2DBounds(1, 1, 4, 4); }
    private record Fixture(FakeRhi fake, Render2DScheduler scheduler) { RenderExecution execution() { return fake.execution(1, 0, 20, 20); } }
    @FunctionalInterface private interface EffectCall { void run(FakeRhi fake, RenderExecution execution); }
}
