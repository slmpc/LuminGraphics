package com.github.slmpc.lumingraphics.render;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.lumingraphics.render.renderer.RendererSet;
import com.github.slmpc.lumingraphics.render.scheduler.GlyphQuad;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DBounds;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScissor;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScheduler;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedPrimitiveTest {
    private static final LuminColor TL = new LuminColor(1, 0, 0, 1);
    private static final LuminColor BL = new LuminColor(0, 1, 0, 1);
    private static final LuminColor BR = new LuminColor(0, 0, 1, 1);
    private static final LuminColor TR = new LuminColor(1, 1, 0, 1);
    private static final Render2DBounds BOUNDS = new Render2DBounds(10, 20, 30, 40);

    @Test
    void vertexColorsAreEmittedAsRgbaBytesForUnormAttributes() {
        LuminColor color = new LuminColor(0x12 / 255.0f, 0x34 / 255.0f, 0x56 / 255.0f, 0x78 / 255.0f);
        byte[] vertex = render(layer -> layer.addRect(new Render2DBounds(1, 2, 3, 4), color)).writes().get(0);

        assertArrayEquals(new byte[]{0x12, 0x34, 0x56, 0x78}, new byte[]{
                vertex[12], vertex[13], vertex[14], vertex[15]
        });
    }

    @Test
    void gradientsAndPerCornerRadiiReachExactVertexAttributes() {
        byte[] rect = render(layer -> layer.addRectGradient(BOUNDS, TL, BL, BR, TR)).writes().get(0);
        assertVertex(rect, 16, 0, 10, 20, TL, 0);
        assertVertex(rect, 16, 1, 10, 60, BL, 0);
        assertVertex(rect, 16, 2, 40, 60, BR, 0);
        assertVertex(rect, 16, 5, 40, 20, TR, 0);

        byte[] round = render(layer -> layer.addRoundRectGradient(BOUNDS, 1, 2, 3, 4,
                TL, BL, BR, TR)).writes().get(0);
        assertVertex(round, 48, 0, 10, 20, TL, 0);
        assertFloatTuple(round, 32, 1, 2, 3, 4);
        assertRgba8(buffer(round), 48 + 12, BL);

        byte[] outline = render(layer -> layer.addOutline(BOUNDS, 4, 3, 2, 1, 1.5f, TL)).writes().get(0);
        assertFloatTuple(outline, 32, 4, 3, 2, 1);
        assertEquals(1.5f, buffer(outline).getFloat(48));

        byte[] shadow = render(layer -> layer.addShadow(BOUNDS, 4, 3, 2, 1, 5, TL)).writes().get(0);
        assertVertex(shadow, 48, 0, 5, 15, TL, 5);
        assertFloatTuple(shadow, 32, 4, 3, 2, 1);
        assertFloatTuple(shadow, 16, 10, 20, 40, 60);
    }

    @Test
    void textureUvRadiiAndCpuRotationsReachExactVertices() {
        Render2DTexture texture = new Render2DTexture.Resource("atlas");
        byte[] rounded = render(layer -> layer.addRoundedTexture(BOUNDS, texture, 1, 2, 3, 4,
                0.1f, 0.2f, 0.8f, 0.9f, TL)).writes().get(0);
        assertVertex(rounded, 56, 0, 10, 20, TL, 0);
        assertFloatTuple(rounded, 16, 0.1f, 0.2f);
        assertFloatTuple(rounded, 40, 1, 2, 3, 4);
        assertFloatTuple(rounded, 56 + 16, 0.1f, 0.9f);

        byte[] rotated = render(layer -> layer.addRotatedTexture(BOUNDS, texture,
                0.1f, 0.2f, 0.8f, 0.9f, TL, 10, 20, 90)).writes().get(0);
        assertVertex(rotated, 56, 0, 10, 20, TL, 0);
        assertVertex(rotated, 56, 1, -30, 20, TL, 0);
        assertVertex(rotated, 56, 2, -30, 50, TL, 0);
        assertFloatTuple(rotated, 16, 0.1f, 0.2f);

        GlyphQuad glyph = new GlyphQuad(BOUNDS, 0.2f, 0.3f, 0.7f, 0.8f, BR);
        byte[] glyphs = render(layer -> layer.addRotatedGlyphs(BOUNDS, texture, List.of(glyph),
                10, 20, 90)).writes().get(0);
        assertGlyphVertex(glyphs, 1, -30, 20, 0.2f, 0.8f, BR);
    }

    @Test
    void chevronProgressAndSegmentPayloadRemainExact() {
        byte[] right = render(layer -> layer.addChevronTriangle(20, 30, 4, 0, TL)).writes().get(0);
        assertVertex(right, 16, 0, 16, 26, TL, 0);
        assertVertex(right, 16, 1, 16, 34, TL, 0);
        assertVertex(right, 16, 2, 24, 30, TL, 0);

        byte[] down = render(layer -> layer.addChevronTriangle(20, 30, 4, 1, TL)).writes().get(0);
        assertVertex(down, 16, 0, 16, 26, TL, 0);
        assertVertex(down, 16, 1, 24, 26, TL, 0);
        assertVertex(down, 16, 2, 20, 34, TL, 0);

        float[] rects = {1, 2, 3, 4, 8, 9, 10, 11};
        float[] radii = {2, 5};
        FakeRhi fake = render(layer -> {
            layer.addSegmentedShadow(BOUNDS, 1, 2, 3, 4, 6, TL, rects, radii, 2);
            rects[0] = 99;
            radii[0] = 99;
        });
        var payload = fake.segmentedPayloads().get(0);
        assertArrayEquals(new float[]{1, 2, 3, 4, 8, 9, 10, 11}, payload.segmentRects());
        assertArrayEquals(new float[]{2, 5}, payload.segmentRadii());
        assertEquals(2, payload.segmentCount());
        assertEquals(List.of("segmented-shadow"), fake.boundPipelines());
        assertTrue(fake.trace().contains("segmentedDescriptor=2"));
    }

    @Test
    void marqueeUsesNestedScissorAndRestoresOuterState() {
        FakeRhi fake = new FakeRhi();
        try (var scheduler = scheduler(fake)) {
            var layer = scheduler.layer(0);
            layer.pushScissor(new Render2DScissor(0, 0, 80, 80));
            layer.addMarqueeGlyphs(BOUNDS, new Render2DScissor(20, 10, 20, 30),
                    new Render2DTexture.Resource("font"),
                    List.of(new GlyphQuad(BOUNDS, 0, 0, 1, 1, TL)));
            layer.addRect(BOUNDS, BR);
            layer.popScissor();
            scheduler.flushAndClear(fake.execution(1, 0, 100, 100));
        }
        assertEquals(List.of("scissor=0,0,80,80", "scissor=20,10,20,30"),
                fake.trace().stream().filter(line -> line.startsWith("scissor=")).toList());
        assertEquals(List.of("rectangle", "ttf-font-aa"), fake.boundPipelines());
    }

    @Test
    void advancedContractsRejectMalformedStateWithoutQueuingWork() {
        try (var scheduler = scheduler(new FakeRhi())) {
            var layer = scheduler.layer(0);
            assertThrows(IllegalArgumentException.class,
                    () -> layer.addRoundRect(BOUNDS, 1, Float.NaN, 3, 4, TL));
            assertThrows(IllegalArgumentException.class,
                    () -> layer.addRoundedTexture(BOUNDS, new Render2DTexture.Resource("x"), 1, 2, 3, 4,
                            -0.1f, 0, 1, 1, TL));
            assertThrows(IllegalArgumentException.class,
                    () -> layer.addRotatedTexture(BOUNDS, new Render2DTexture.Resource("x"),
                            0, 0, 1, 1, TL, 0, 0, Float.POSITIVE_INFINITY));
            assertThrows(IllegalArgumentException.class,
                    () -> layer.addChevronTriangle(0, 0, 1, 1.01f, TL));
            assertThrows(IllegalArgumentException.class,
                    () -> layer.addSegmentedShadow(BOUNDS, 1, 1, 1, 1, 2, TL,
                            new float[]{0, 0, -1, 2}, new float[]{1}, 1));
            assertThrows(IllegalArgumentException.class,
                    () -> layer.addSegmentedShadow(BOUNDS, 1, 1, 1, 1, 2, TL,
                            new float[]{Float.MAX_VALUE, 0, Float.MAX_VALUE, 1}, new float[]{1}, 1));
            assertThrows(IllegalArgumentException.class,
                    () -> layer.addSegmentedShadow(BOUNDS, 1, 1, 1, 1, 2, TL,
                            new float[]{0, Float.MAX_VALUE, 1, Float.MAX_VALUE}, new float[]{1}, 1));
            assertThrows(IllegalArgumentException.class,
                    () -> layer.addSegmentedShadow(BOUNDS, 1, 1, 1, 1, 2, TL,
                            new float[]{Float.NaN, 0, 1, 1}, new float[]{1}, 1));
            assertThrows(IllegalArgumentException.class,
                    () -> layer.addSegmentedShadow(BOUNDS, 1, 1, 1, 1, 2, TL,
                            new float[]{0, Float.POSITIVE_INFINITY, 1, 1}, new float[]{1}, 1));
            assertThrows(IllegalArgumentException.class, () -> layer.addRectOutline(null, 1, TL));
            assertTrue(scheduler.isEmpty());
        }
        float nearMax = Float.MAX_VALUE / 2;
        var finiteNearMax = new Render2DCommand.SegmentedShadow(0, 0, BOUNDS, null,
                1, 1, 1, 1, 2, TL,
                new float[]{nearMax, nearMax, nearMax, nearMax}, new float[]{1}, 1);
        assertArrayEquals(new float[]{nearMax, nearMax, nearMax, nearMax}, finiteNearMax.segmentRects());
        var negativeCoordinates = new Render2DCommand.SegmentedShadow(0, 0, BOUNDS, null,
                1, 1, 1, 1, 2, TL,
                new float[]{-Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE},
                new float[]{1}, 1);
        assertArrayEquals(new float[]{-Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE},
                negativeCoordinates.segmentRects());
        FakeRhi flipped = render(layer -> layer.addTexture(BOUNDS, new Render2DTexture.Resource("flip"),
                1, 1, 0, 0, TL));
        assertFloatTuple(flipped.writes().get(0), 16, 1, 1);
        assertFloatTuple(flipped.writes().get(0), 56 * 2 + 16, 0, 0);
    }

    @Test
    void stateChangesBreakBatchesAndBackendFailureEndsEveryFrame() {
        FakeRhi fake = new FakeRhi();
        try (var scheduler = scheduler(fake)) {
            var layer = scheduler.layer(0);
            layer.addRoundedTexture(BOUNDS, new Render2DTexture.Resource("a"), 1, 1, 1, 1, 0, 0, 1, 1, TL);
            layer.addRoundedTexture(BOUNDS, new Render2DTexture.Resource("b"), 1, 1, 1, 1, 0, 0, 1, 1, TL);
            layer.pushScissor(new Render2DScissor(0, 0, 50, 50));
            layer.addRoundedTexture(BOUNDS, new Render2DTexture.Resource("b"), 1, 1, 1, 1, 0, 0, 1, 1, TL);
            layer.popScissor();
            scheduler.flushAndClear(fake.execution(1, 0, 100, 100));
            assertEquals(List.of("texture", "texture", "texture"), fake.boundPipelines());
        }

        FakeRhi shadows = new FakeRhi();
        try (var scheduler = scheduler(shadows)) {
            var layer = scheduler.layer(0);
            layer.addShadow(BOUNDS, 2, 3, TL);
            layer.addSegmentedShadow(BOUNDS, 1, 2, 3, 4, 3, BL,
                    new float[]{10, 20, 30, 40}, new float[]{2}, 1);
            layer.addSegmentedShadow(BOUNDS, 4, 3, 2, 1, 5, BR,
                    new float[]{11, 21, 31, 41}, new float[]{3}, 1);
            scheduler.flushAndClear(shadows.execution(1, 0, 100, 100));
            assertEquals(List.of("shadow", "segmented-shadow", "segmented-shadow"), shadows.boundPipelines());
            assertEquals(2, shadows.segmentedPayloads().size());
        }

        FakeRhi failing = new FakeRhi();
        RendererSet renderers = RendererSet.create(failing.resources(), 4096);
        try (var scheduler = new Render2DScheduler(renderers, 8)) {
            scheduler.layer(0).addRotatedTexture(BOUNDS, new Render2DTexture.Resource("a"),
                    0, 0, 1, 1, TL, 0, 0, 45);
            failing.failNextDraw();
            assertThrows(IllegalStateException.class,
                    () -> scheduler.flushAndClear(failing.execution(1, 0, 100, 100)));
            assertTrue(scheduler.isEmpty());
            assertTrue(renderers.allFramesEnded());
        }
    }

    private static FakeRhi render(java.util.function.Consumer<Render2DScheduler.LayerHandle> command) {
        FakeRhi fake = new FakeRhi();
        try (var scheduler = scheduler(fake)) {
            command.accept(scheduler.layer(0));
            scheduler.flushAndClear(fake.execution(1, 0, 200, 200));
        }
        return fake;
    }

    private static Render2DScheduler scheduler(FakeRhi fake) {
        return new Render2DScheduler(RendererSet.create(fake.resources(), 64 * 1024), 8);
    }

    private static void assertVertex(byte[] bytes, int stride, int index, float x, float y,
                                     LuminColor color, float z) {
        ByteBuffer buffer = buffer(bytes);
        int offset = stride * index;
        assertEquals(x, buffer.getFloat(offset));
        assertEquals(y, buffer.getFloat(offset + 4));
        assertEquals(z, buffer.getFloat(offset + 8));
        assertRgba8(buffer, offset + 12, color);
    }

    private static void assertFloatTuple(byte[] bytes, int offset, float... expected) {
        ByteBuffer buffer = buffer(bytes);
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], buffer.getFloat(offset + i * 4));
    }

    private static void assertGlyphVertex(byte[] bytes, int index, float x, float y,
                                          float u, float v, LuminColor color) {
        ByteBuffer buffer = buffer(bytes);
        int offset = index * 24;
        assertEquals(x, buffer.getFloat(offset));
        assertEquals(y, buffer.getFloat(offset + 4));
        assertEquals(0, buffer.getFloat(offset + 8));
        assertEquals(u, buffer.getFloat(offset + 12));
        assertEquals(v, buffer.getFloat(offset + 16));
        assertRgba8(buffer, offset + 20, color);
    }

    private static void assertRgba8(ByteBuffer buffer, int offset, LuminColor color) {
        int rgba = color.toRgba8();
        assertEquals((byte) (rgba >>> 24), buffer.get(offset));
        assertEquals((byte) (rgba >>> 16), buffer.get(offset + 1));
        assertEquals((byte) (rgba >>> 8), buffer.get(offset + 2));
        assertEquals((byte) rgba, buffer.get(offset + 3));
    }

    private static ByteBuffer buffer(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }
}
