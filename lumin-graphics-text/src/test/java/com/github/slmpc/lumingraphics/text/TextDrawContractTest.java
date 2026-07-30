package com.github.slmpc.lumingraphics.text;

import static org.junit.jupiter.api.Assertions.*;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TextDrawContractTest {
    private static final FontResource FONT = FontResource.classpath("assets/lumin_graphics/fonts/font.ttf");

    @Test
    void styledAndRotatedDrawsReachSinkExactlyInAddOrder() {
        CountingUploader uploader = new CountingUploader();
        List<Snapshot> submitted = new ArrayList<>();
        LuminColor regularColor = new LuminColor(0.1f, 0.2f, 0.3f, 0.4f);
        LuminColor rotatedColor = new LuminColor(0.7f, 0.6f, 0.5f, 1.0f);
        try (TtfFontLoader font = font(uploader);
             TtfTextRenderer renderer = new TtfTextRenderer(draws -> draws.forEach(draw ->
                     submitted.add(Snapshot.capture(draw))))) {
            TextLayout regular = renderer.add("A", 11, 13, 0.75f, regularColor, font);
            TextLayout rotated = renderer.addRotated(
                    "B", 17, 19, 1.25f, rotatedColor, font, 23, 29, -37.5f);
            TextLayout legacy = renderer.add("C", 31, 37, 0.5f, font);

            renderer.draw();

            assertEquals(List.of(
                    new Snapshot(11, 13, 0.75f, regularColor, 11, 13, 0,
                            regular.width(), regular.height(), regular.glyphCount(),
                            regular.glyphRevision(), regular.atlasRevision(), regular.stableHash()),
                    new Snapshot(17, 19, 1.25f, rotatedColor, 23, 29, -37.5f,
                            rotated.width(), rotated.height(), rotated.glyphCount(),
                            rotated.glyphRevision(), rotated.atlasRevision(), rotated.stableHash()),
                    new Snapshot(31, 37, 0.5f, new LuminColor(1, 1, 1, 1), 31, 37, 0,
                            legacy.width(), legacy.height(), legacy.glyphCount(),
                            legacy.glyphRevision(), legacy.atlasRevision(), legacy.stableHash())),
                    submitted);
            assertEquals(3, submitted.size(), "one descriptor per add must preserve insertion order");
        }
        assertEquals(uploader.uploads.get(), uploader.closes.get());
    }

    @Test
    void sinkBorrowsDrawsAndRendererClosesThemAfterSuccessAndFailure() {
        CountingUploader successUploader = new CountingUploader();
        List<TextDraw> borrowed = new ArrayList<>();
        try (TtfFontLoader font = font(successUploader);
             TtfTextRenderer renderer = new TtfTextRenderer(borrowed::addAll)) {
            renderer.add("A", 0, 0, 1, new LuminColor(1, 1, 1, 1), font);
            renderer.draw();
            assertThrows(FontClosedException.class, () -> borrowed.get(0).batches());
            assertThrows(FontClosedException.class, () -> borrowed.get(0).batches().get(0).upload());
            renderer.draw();
            assertEquals(1, borrowed.size(), "a successful draw consumes the pending queue exactly once");
        }
        assertEquals(successUploader.uploads.get(), successUploader.closes.get());

        CountingUploader failureUploader = new CountingUploader();
        List<TextDraw> failed = new ArrayList<>();
        try (TtfFontLoader font = font(failureUploader);
             TtfTextRenderer renderer = new TtfTextRenderer(draws -> {
                 failed.addAll(draws);
                 throw new IllegalStateException("sink failed");
             })) {
            renderer.addRotated("B", 1, 2, 1, new LuminColor(0, 0, 0, 1), font, 3, 4, 5);
            assertThrows(IllegalStateException.class, renderer::draw);
            assertThrows(FontClosedException.class, () -> failed.get(0).batches());
            renderer.draw();
        }
        assertEquals(failureUploader.uploads.get(), failureUploader.closes.get());
    }

    @Test
    void retainedDrawKeepsExactUploadAliveAcrossDelayedFlushAndAtlasRevision() {
        CountingUploader uploader = new CountingUploader();
        List<TextDraw> delayed = new ArrayList<>();
        GlyphAtlasUpload[] borrowedUpload = new GlyphAtlasUpload[1];
        List<GlyphPlacement> borrowedPlacements = new ArrayList<>();
        LuminColor color = new LuminColor(0.1f, 0.2f, 0.3f, 1);
        try (TtfFontLoader font = font(uploader);
             TtfTextRenderer renderer = new TtfTextRenderer(draws -> draws.forEach(draw -> {
                 borrowedUpload[0] = draw.batches().get(0).upload();
                 borrowedPlacements.addAll(draw.batches().get(0).glyphs());
                 delayed.add(draw.retain());
             }))) {
            TextLayout submitted = renderer.addRotated("A", 11, 13, 0.75f, color, font, 17, 19, 23);
            renderer.draw();

            TextDraw retained = delayed.get(0);
            GlyphAtlasUpload retainedUpload = retained.batches().get(0).upload();
            assertSame(borrowedUpload[0], retainedUpload, "retention must preserve the exact submitted upload");
            assertEquals(borrowedPlacements, retained.batches().get(0).glyphs());
            assertEquals(new Snapshot(11, 13, 0.75f, color, 17, 19, 23,
                    submitted.width(), submitted.height(), submitted.glyphCount(), submitted.glyphRevision(),
                    submitted.atlasRevision(), submitted.stableHash()), Snapshot.capture(retained));
            font.requireGlyph('B');
            assertEquals(2, uploader.uploads.get(), "atlas mutation must publish a new upload");
            assertFalse(retainedUpload.isClosed(), "delayed draw must keep the retired upload readable");
            assertNotNull(retainedUpload.texture());

            retained.close();
            retained.close();
            assertTrue(retainedUpload.isClosed(), "delayed upload must close after its retained draw");
            assertEquals(1, uploader.closes.get(), "retained upload owner must close exactly once");
            assertThrows(FontClosedException.class, retained::retain);
            assertThrows(FontClosedException.class, retained::batches);
        }
        assertEquals(uploader.uploads.get(), uploader.closes.get());
    }

    @Test
    void retainRollsBackEarlierBatchesWhenLaterBatchIsClosed() {
        CountingUploader uploader = new CountingUploader();
        TtfGlyphAtlas firstAtlas = atlasWithUpload(0, uploader);
        TtfGlyphAtlas secondAtlas = atlasWithUpload(1, uploader);
        TextRenderBatch first = new TextRenderBatch(firstAtlas, List.of());
        TextRenderBatch second = new TextRenderBatch(secondAtlas, List.of());
        TextDraw draw = new TextDraw(1, 2, 1, new LuminColor(1, 1, 1, 1), 1, 2, 0,
                new TextLayout(0, 0, 0, 0, 0, 0, List.of(first, second)));
        second.close();

        assertThrows(FontClosedException.class, draw::retain);
        draw.close();
        firstAtlas.close();
        secondAtlas.close();
        assertEquals(uploader.uploads.get(), uploader.closes.get(),
                "partial retention must release every freshly acquired lease");
    }

    @Test
    void malformedDrawArgumentsFailBeforeGlyphOrSinkWork() {
        CountingUploader uploader = new CountingUploader();
        AtomicInteger sinkCalls = new AtomicInteger();
        try (TtfFontLoader font = font(uploader);
             TtfTextRenderer renderer = new TtfTextRenderer(ignored -> sinkCalls.incrementAndGet())) {
            LuminColor white = new LuminColor(1, 1, 1, 1);
            assertThrows(NullPointerException.class, () -> renderer.add("A", 0, 0, 1, null, font));
            assertThrows(NullPointerException.class, () -> renderer.add("A", 0, 0, 1, white, null));
            assertThrows(IllegalArgumentException.class,
                    () -> renderer.addRotated("A", 0, 0, 1, white, font, Float.NaN, 0, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> renderer.addRotated("A", 0, 0, 1, white, font, 0, 0, Float.POSITIVE_INFINITY));
            assertThrows(IllegalArgumentException.class,
                    () -> renderer.add("A", 0, 0, Float.NaN, white, font));
            renderer.draw();
        }
        assertEquals(0, sinkCalls.get());
        assertEquals(0, uploader.uploads.get());
        assertEquals(0, uploader.closes.get());
    }

    private static TtfFontLoader font(CountingUploader uploader) {
        return new TtfFontLoader(FONT, 48, 4, 96, 96, 4, uploader, Runnable::run);
    }

    private static TtfGlyphAtlas atlasWithUpload(int page, CountingUploader uploader) {
        TtfGlyphAtlas atlas = new TtfGlyphAtlas(page, 8, 8, uploader);
        atlas.append(new TtfGlyph('A', 1, 1, 0, 0, 1, new byte[] { 1 }));
        return atlas;
    }

    private record Snapshot(float x, float y, float scale, LuminColor color,
                            float originX, float originY, float rotationDegrees,
                            float width, float height, int glyphCount,
                            long glyphRevision, long atlasRevision, long stableHash) {
        static Snapshot capture(TextDraw draw) {
            assertFalse(draw.batches().isEmpty());
            assertEquals(draw.glyphCount(), draw.batches().stream().mapToInt(TextRenderBatch::glyphCount).sum());
            return new Snapshot(draw.x(), draw.y(), draw.scale(), draw.color(),
                    draw.originX(), draw.originY(), draw.rotationDegrees(), draw.width(), draw.height(),
                    draw.glyphCount(), draw.glyphRevision(), draw.atlasRevision(), draw.stableHash());
        }
    }

    private static final class CountingUploader implements GlyphAtlasUploader {
        private final AtomicInteger uploads = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        @Override public GlyphAtlasUpload upload(AtlasPixels pixels) {
            int id = uploads.incrementAndGet();
            return new GlyphAtlasUpload("draw-contract-" + id, closes::incrementAndGet);
        }
    }
}
