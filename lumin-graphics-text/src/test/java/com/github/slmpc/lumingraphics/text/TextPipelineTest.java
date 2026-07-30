package com.github.slmpc.lumingraphics.text;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.DisplayName;

class TextPipelineTest {
    private static final FontResource FONT = FontResource.classpath("assets/lumin_graphics/fonts/font.ttf");

    @Test
    @DisplayName("MIG-TEXT-TTF-FILE and MIG-TEXT-TTF-GLYPH real STB behavior")
    void opensBundledFontsAndReportsConsistentRealStbMetrics() {
        for (String name : List.of("font.ttf", "icons.ttf", "jura-light.ttf", "osakachips.ttf")) {
            try (TtfFontFile font = TtfFontFile.open(
                    FontResource.classpath("assets/lumin_graphics/fonts/" + name), 48, 4)) {
                assertTrue(font.metrics().ascent() > 0);
                assertTrue(font.metrics().lineHeight() > 0);
                assertTrue(font.scale() > 0.0f);
                assertTrue(font.hasGlyph('A'));
                assertEquals(font.advance('A') + font.kerning('A', 'V') + font.advance('V'),
                        font.measureAdvance("AV"));
                assertTrue(font.rasterize('A').pixels().length > 0);
            }
        }
    }

    @Test
    void malformedAndMissingGlyphsHaveTypedErrors(@TempDir Path tempDir) throws Exception {
        Path malformed = tempDir.resolve("malformed.ttf");
        Files.write(malformed, new byte[] {1, 2, 3, 4});
        assertThrows(FontMalformedException.class,
                () -> TtfFontFile.open(FontResource.path(malformed), 48, 4));
        try (TtfFontFile font = TtfFontFile.open(FONT, 48, 4)) {
            assertThrows(MissingGlyphException.class, () -> font.rasterize(0x10FFFF));
        }
    }

    @RepeatedTest(10)
    @DisplayName("MIG-TEXT-FONT-LOADER and MIG-TEXT-TTF-LOADER async dedup")
    void concurrentRequestsDeduplicateAndCloseCancelsPendingWork() throws Exception {
        CountingUploader uploader = new CountingUploader();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try (TtfFontLoader loader = new TtfFontLoader(FONT, 48, 4, 128, 128, 4, uploader, executor)) {
            List<CompletableFuture<GlyphDescriptor>> requests = new ArrayList<>();
            for (int i = 0; i < 32; i++) requests.add(loader.requestGlyph('W'));
            assertNotSame(requests.get(0), requests.get(31));
            GlyphDescriptor glyph = requests.get(0).get();
            assertEquals('W', glyph.codepoint());
            assertEquals(1, loader.rasterizationCount());
            assertEquals(1, loader.glyphRevision());
            assertEquals(1, loader.atlasRevision());
            assertEquals(1, uploader.uploads.get());
            assertSame(glyph, loader.requestGlyph('W').join());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
        assertEquals(1, uploader.closed.get());

        ExecutorService blocked = Executors.newSingleThreadExecutor();
        CompletableFuture<Void> gate = new CompletableFuture<>();
        blocked.submit(gate::join);
        TtfFontLoader closing = new TtfFontLoader(FONT, 48, 4, 64, 64, 2,
                new CountingUploader(), blocked);
        CompletableFuture<GlyphDescriptor> pending = closing.requestGlyph('Q');
        closing.close();
        gate.complete(null);
        assertThrows(FontClosedException.class, () -> closing.requestGlyph('A'));
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertTrue(pending.isCompletedExceptionally()));
        blocked.shutdownNow();
        assertTrue(blocked.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("MIG-TEXT-GLYPH-DESCRIPTOR and MIG-TEXT-TTF-ATLAS revision behavior")
    void atlasGrowsRevisionsAndInvalidatesLayoutWithoutLeakingUploads() {
        CountingUploader uploader = new CountingUploader();
        try (TtfFontLoader loader = new TtfFontLoader(FONT, 48, 4, 64, 64, 8, uploader, Runnable::run)) {
            TextLayoutEngine engine = new TextLayoutEngine();
            TextLayout first = engine.layout("AB", 10, 20, 1.0f, loader);
            assertEquals(2, first.glyphCount());
            long firstHash = first.stableHash();
            long firstAtlasRevision = loader.atlasRevision();
            loader.requireGlyph('C');
            assertTrue(loader.atlasRevision() > firstAtlasRevision);
            TextLayout second = engine.layout("AB", 10, 20, 1.0f, loader);
            assertNotEquals(first.atlasRevision(), second.atlasRevision());
            assertEquals(firstHash, second.stableHash());
            assertTrue(uploader.closed.get() < uploader.uploads.get());
            closeBatches(first);
            closeBatches(second);
        }
        assertEquals(uploader.uploads.get(), uploader.closed.get());
    }

    @Test
    void deterministicMultilineMeasurementAndAtlasBatches() {
        CountingUploader uploader = new CountingUploader();
        try (TtfFontLoader loader = new TtfFontLoader(FONT, 48, 4, 96, 96, 8, uploader, Runnable::run)) {
            TextLayoutEngine engine = new TextLayoutEngine();
            TextMeasurement measured = engine.measure("AVA\nW", 0.5f, loader);
            TextLayout a = engine.layout("AVA\nW", 3.25f, 7.5f, 0.5f, loader);
            TextLayout b = engine.layout("AVA\nW", 3.25f, 7.5f, 0.5f, loader);
            assertEquals(measured.width(), a.width());
            assertEquals(measured.height(), a.height());
            assertEquals(4, a.glyphCount());
            assertEquals(a, b);
            assertEquals(a.stableHash(), b.stableHash());
            assertFalse(a.batches().isEmpty());
            assertEquals(4, a.batches().stream().mapToInt(TextRenderBatch::glyphCount).sum());
            closeBatches(a);
            closeBatches(b);
        }
    }

    @Test
    void stableLayoutsReuseImmutableCacheDataAndRevisionChangesInvalidateOnlyAffectedFont() {
        CountingUploader firstUploader = new CountingUploader();
        CountingUploader otherUploader = new CountingUploader();
        TextLayoutEngine engine = new TextLayoutEngine();
        try (TtfFontLoader firstFont = new TtfFontLoader(FONT, 48, 4, 96, 96, 4, firstUploader, Runnable::run);
             TtfFontLoader otherFont = new TtfFontLoader(FONT, 48, 4, 96, 96, 4, otherUploader, Runnable::run)) {
            TextLayout first = engine.layout("AB", 3, 5, 0.75f, firstFont);
            TextLayout stable = engine.layout("AB", 3, 5, 0.75f, firstFont);
            assertNotSame(first.batches().get(0), stable.batches().get(0), "callers need independent leases");
            assertSame(first.batches().get(0).glyphs(), stable.batches().get(0).glyphs(),
                    "stable inputs must reuse cached immutable placements");

            TextLayout unrelated = engine.layout("XY", 7, 11, 0.5f, otherFont);
            firstFont.requireGlyph('C');
            TextLayout invalidated = engine.layout("AB", 3, 5, 0.75f, firstFont);
            assertNotEquals(first.atlasRevision(), invalidated.atlasRevision());
            assertNotSame(first.batches().get(0).glyphs(), invalidated.batches().get(0).glyphs(),
                    "font revision changes must recompute the affected cache key");
            assertEquals(first.stableHash(), invalidated.stableHash());
            TextLayout unrelatedStable = engine.layout("XY", 7, 11, 0.5f, otherFont);
            assertSame(unrelated.batches().get(0).glyphs(), unrelatedStable.batches().get(0).glyphs(),
                    "an unrelated font key must remain reusable");

            engine.clear();
            TextLayout afterClear = engine.layout("XY", 7, 11, 0.5f, otherFont);
            assertNotSame(unrelated.batches().get(0).glyphs(), afterClear.batches().get(0).glyphs());
            closeBatches(first);
            closeBatches(stable);
            closeBatches(unrelated);
            closeBatches(invalidated);
            closeBatches(unrelatedStable);
            closeBatches(afterClear);
        }
        engine.close();
        try (TtfFontLoader probe = new TtfFontLoader(
                FONT, 48, 4, 96, 96, 4, new CountingUploader(), Runnable::run)) {
            assertThrows(FontClosedException.class, () -> engine.layout("AB", 3, 5, 0.75f, probe));
        }
    }

    @Test
    void partialMultiAtlasAcquisitionRollsBackEarlierLeaseAndSuppressesCleanupFailure() {
        AtomicBoolean throwOnClose = new AtomicBoolean();
        AtomicInteger firstCloseAttempts = new AtomicInteger();
        TtfGlyphAtlas first = new TtfGlyphAtlas(0, 8, 8, pixels -> new GlyphAtlasUpload("first", () -> {
            firstCloseAttempts.incrementAndGet();
            if (throwOnClose.get()) throw new IllegalStateException("first cleanup failed");
        }));
        TtfGlyphAtlas second = new TtfGlyphAtlas(1, 8, 8,
                pixels -> new GlyphAtlasUpload("second", () -> {}));
        first.append(tinyGlyph('A'));
        second.append(tinyGlyph('B'));
        GlyphAtlasUpload firstUpload = first.upload();
        Map<TtfGlyphAtlas, List<GlyphPlacement>> closingMap = closeAtlasesBeforeSecondEntry(
                first, second, throwOnClose);

        FontClosedException failure = assertThrows(FontClosedException.class,
                () -> TextLayoutEngine.acquireBatches(closingMap));
        assertEquals(1, failure.getSuppressed().length, "rollback failure must be retained on the original error");
        assertInstanceOf(FontException.class, failure.getSuppressed()[0]);
        assertTrue(firstUpload.isClosed(), "the first acquired lease must be released after the second retain fails");
        assertEquals(1, firstCloseAttempts.get(), "the first upload owner must close exactly once");
    }

    @Test
    void retainedBatchesKeepExactUploadAliveUntilLastLeaseCloses() throws Exception {
        CountingUploader uploader = new CountingUploader();
        try (TtfFontLoader loader = new TtfFontLoader(FONT, 48, 4, 96, 96, 4, uploader, Runnable::run)) {
            TextLayoutEngine engine = new TextLayoutEngine();
            TextRenderBatch first = engine.layout("A", 0, 0, 1, loader).batches().get(0);
            TextRenderBatch second = engine.layout("A", 0, 0, 1, loader).batches().get(0);
            GlyphAtlasUpload queuedUpload = first.upload();
            assertSame(queuedUpload, second.upload());
            loader.requireGlyph('B');
            assertFalse(queuedUpload.isClosed(), "atlas mutation must not retire a leased upload");
            ((AutoCloseable) (Object) first).close();
            assertFalse(queuedUpload.isClosed(), "one remaining batch lease must keep the upload alive");
            ((AutoCloseable) (Object) second).close();
            assertTrue(queuedUpload.isClosed(), "the last batch lease must retire the old upload exactly once");
        }
    }

    @Test
    void rendererReleasesPendingBatchWhenDrawFailsAndWhenCleared() {
        CountingUploader uploader = new CountingUploader();
        try (TtfFontLoader loader = new TtfFontLoader(FONT, 48, 4, 96, 96, 4, uploader, Runnable::run);
             TtfTextRenderer renderer = new TtfTextRenderer(ignored -> { throw new IllegalStateException("sink failed"); })) {
            TextRenderBatch failed = renderer.add("A", 0, 0, 1, loader).batches().get(0);
            loader.requireGlyph('B');
            assertThrows(IllegalStateException.class, renderer::draw);
            assertTrue(failed.upload().isClosed(), "draw failure must release pending batches");

            TextRenderBatch cleared = renderer.add("C", 0, 0, 1, loader).batches().get(0);
            loader.requireGlyph('D');
            renderer.clear();
            assertTrue(cleared.upload().isClosed(), "clear must release pending batches");
        }
    }

    @Test
    void callerCancellationStopsQueuedAndActiveRasterTasksBeforeAtlasCommit() throws Exception {
        ExecutorService queuedExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch queuedGate = new CountDownLatch(1);
        queuedExecutor.submit(() -> awaitUninterruptibly(queuedGate));
        try (TtfFontLoader loader = new TtfFontLoader(FONT, 48, 4, 64, 64, 2,
                new CountingUploader(), queuedExecutor)) {
            CompletableFuture<GlyphDescriptor> queued = loader.requestGlyph('Q');
            assertTrue(queued.cancel(true));
            queuedGate.countDown();
            queuedExecutor.shutdown();
            assertTrue(queuedExecutor.awaitTermination(2, TimeUnit.SECONDS), "cancelled queued task must terminate");
            assertEquals(0, loader.rasterizationCount(), "cancelled queued task must never enter STB");
            assertEquals(0, loader.glyphRevision());
            assertEquals(0, loader.atlasRevision());
        } finally {
            queuedGate.countDown();
            queuedExecutor.shutdownNow();
        }

        BlockingUploader uploader = new BlockingUploader();
        ExecutorService activeExecutor = Executors.newSingleThreadExecutor();
        try (TtfFontLoader loader = new TtfFontLoader(FONT, 48, 4, 64, 64, 2, uploader, activeExecutor)) {
            CompletableFuture<GlyphDescriptor> active = loader.requestGlyph('R');
            assertTrue(uploader.entered.await(2, TimeUnit.SECONDS), "task must reach the active upload phase");
            assertTrue(active.cancel(true));
            boolean interrupted = uploader.interrupted.await(2, TimeUnit.SECONDS);
            uploader.release.countDown();
            activeExecutor.shutdown();
            assertTrue(activeExecutor.awaitTermination(2, TimeUnit.SECONDS), "cancelled active task must terminate");
            assertTrue(interrupted, "caller cancellation must interrupt active owned work");
            assertEquals(0, loader.glyphRevision(), "cancelled active work must not commit a glyph");
            assertEquals(0, loader.atlasRevision(), "cancelled active work must not commit an atlas revision");
            assertEquals(uploader.uploads.get(), uploader.closed.get(), "cancelled upload must release its resource");
        } finally {
            uploader.release.countDown();
            activeExecutor.shutdownNow();
        }
    }

    @Test
    void cancellingOneDeduplicatedSubscriberPreservesSharedRasterWork() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        BlockingUploader uploader = new BlockingUploader();
        try (TtfFontLoader loader = new TtfFontLoader(FONT, 48, 4, 64, 64, 2, uploader, executor)) {
            CompletableFuture<GlyphDescriptor> cancelled = loader.requestGlyph('S');
            CompletableFuture<GlyphDescriptor> remaining = loader.requestGlyph('S');
            assertNotSame(cancelled, remaining, "deduplicated callers need independent cancellation handles");
            assertTrue(uploader.entered.await(2, TimeUnit.SECONDS));
            assertTrue(cancelled.cancel(true));
            assertFalse(uploader.interrupted.await(200, TimeUnit.MILLISECONDS),
                    "one subscriber must not interrupt work needed by another");
            uploader.release.countDown();
            assertEquals('S', remaining.join().codepoint());
            assertEquals(1, loader.rasterizationCount());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            fail(error);
        } finally {
            uploader.release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void lateDeduplicatedSubscriberReplaysTerminalFailureBeforePendingRemoval() throws Exception {
        BlockingFailUploader uploader = new BlockingFailUploader();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (TtfFontLoader loader = new TtfFontLoader(FONT, 48, 4, 64, 64, 2, uploader, executor)) {
            CompletableFuture<GlyphDescriptor> first = loader.requestGlyph('T');
            assertTrue(uploader.entered.await(2, TimeUnit.SECONDS));
            CompletableFuture<CompletableFuture<GlyphDescriptor>> late = new CompletableFuture<>();
            first.whenComplete((ignored, failure) -> late.complete(loader.requestGlyph('T')));
            uploader.release.countDown();
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                assertThrows(CompletionException.class, first::join);
                assertThrows(CompletionException.class, () -> late.join().join());
            });
        } finally {
            uploader.release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void closeCancelsQueuedRasterTaskBeforeItTouchesStb() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletableFuture<Void> gate = new CompletableFuture<>();
        executor.submit(gate::join);
        TtfFontLoader loader = new TtfFontLoader(FONT, 48, 4, 64, 64, 2,
                new CountingUploader(), executor);
        CompletableFuture<GlyphDescriptor> queued = loader.requestGlyph('Q');
        loader.close();
        gate.complete(null);
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertEquals(0, loader.rasterizationCount(), "cancelled task must never enter STB");
        CompletionException failure = assertThrows(CompletionException.class, queued::join);
        assertInstanceOf(FontClosedException.class, failure.getCause());
    }

    @Test
    @DisplayName("MIG-TEXT-FONT-REGISTRY and MIG-TEXT-EMOJI-ATLAS concrete ownership")
    void registryAndAwtEmojiOwnTheirUploadsAndRejectClosedUse() {
        CountingUploader uploader = new CountingUploader();
        try (FontRegistry registry = new FontRegistry()) {
            TtfFontLoader font = registry.register("default",
                    () -> new TtfFontLoader(FONT, 48, 4, 128, 128, 4, uploader, Runnable::run));
            assertSame(font, registry.require("default"));
            assertThrows(IllegalArgumentException.class, () -> registry.register("default", () -> font));
        }
        assertThrows(FontClosedException.class, () -> {
            FontRegistry closed = new FontRegistry();
            closed.close();
            closed.require("default");
        });

        CountingUploader emojiUploader = new CountingUploader();
        try (SystemEmojiAtlas emoji = new SystemEmojiAtlas(64, 64, 24, emojiUploader)) {
            EmojiGlyph glyph = emoji.require(0x1F642);
            assertEquals(0x1F642, glyph.codepoint());
            assertTrue(glyph.width() > 0 && glyph.height() > 0);
            assertSame(glyph, emoji.require(0x1F642));
            assertEquals(1, emojiUploader.uploads.get());
            assertEquals(AtlasPixelFormat.RGBA8, emojiUploader.lastFormat);
        }
        assertEquals(emojiUploader.uploads.get(), emojiUploader.closed.get());
    }

    @Test
    void uploadFailureAndAtlasExhaustionAreTypedAndLeakFree() {
        GlyphAtlasUploader failing = pixels -> { throw new IllegalStateException("device lost"); };
        try (TtfFontLoader loader = new TtfFontLoader(FONT, 48, 4, 64, 64, 1, failing, Runnable::run)) {
            assertThrows(GlyphUploadException.class, () -> loader.requireGlyph('A'));
        }

        CountingUploader uploader = new CountingUploader();
        try (TtfFontLoader loader = new TtfFontLoader(FONT, 48, 4, 8, 8, 1, uploader, Runnable::run)) {
            assertThrows(AtlasExhaustedException.class, () -> loader.requireGlyph('W'));
        }
        assertEquals(uploader.uploads.get(), uploader.closed.get());
    }

    @Test
    void closeAggregatesOwnerFailuresAndStillFreesEveryUploadAndNativeFont() throws Exception {
        ThrowingCloseUploader uploader = new ThrowingCloseUploader();
        TtfFontLoader loader = new TtfFontLoader(FONT, 48, 4, 64, 64, 4, uploader, Runnable::run);
        for (int codepoint = 'A'; codepoint <= 'Z' && uploader.live.get() < 3; codepoint++) {
            loader.requireGlyph(codepoint);
        }
        assertTrue(uploader.live.get() >= 3, "fixture must own multiple live atlas uploads");
        int liveBeforeClose = uploader.live.get();
        uploader.throwOnClose = true;

        FontException failure = assertThrows(FontException.class, loader::close);
        assertEquals(liveBeforeClose - 1, failure.getSuppressed().length,
                "first close failure must remain primary and later failures must be suppressed");
        assertEquals(0, uploader.live.get(), "every later upload owner must still be closed");
        assertEquals(uploader.uploads.get(), uploader.closeAttempts.get(), "each owner must close exactly once");

        Field fontField = TtfFontLoader.class.getDeclaredField("font");
        fontField.setAccessible(true);
        TtfFontFile nativeFont = (TtfFontFile) fontField.get(loader);
        assertThrows(FontClosedException.class, nativeFont::metrics, "native STB font must always be freed");
        loader.close();
        assertEquals(uploader.uploads.get(), uploader.closeAttempts.get(), "retry must remain idempotent");
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException error) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static TtfGlyph tinyGlyph(int codepoint) {
        return new TtfGlyph(codepoint, 1, 1, 0, 0, 1, new byte[] { 1 });
    }

    private static Map<TtfGlyphAtlas, List<GlyphPlacement>> closeAtlasesBeforeSecondEntry(
            TtfGlyphAtlas first, TtfGlyphAtlas second, AtomicBoolean throwOnClose) {
        List<Map.Entry<TtfGlyphAtlas, List<GlyphPlacement>>> entries = List.of(
                Map.entry(first, List.of(new GlyphPlacement('A', 0, 0, 1, 1, new GlyphUv(0, 0, 1, 1)))),
                Map.entry(second, List.of(new GlyphPlacement('B', 1, 0, 2, 1, new GlyphUv(0, 0, 1, 1)))));
        return new AbstractMap<>() {
            @Override public Set<Entry<TtfGlyphAtlas, List<GlyphPlacement>>> entrySet() {
                return new AbstractSet<>() {
                    @Override public int size() { return entries.size(); }
                    @Override public Iterator<Entry<TtfGlyphAtlas, List<GlyphPlacement>>> iterator() {
                        return new Iterator<>() {
                            private int index;
                            @Override public boolean hasNext() { return index < entries.size(); }
                            @Override public Entry<TtfGlyphAtlas, List<GlyphPlacement>> next() {
                                if (index == 1) {
                                    throwOnClose.set(true);
                                    first.close();
                                    second.close();
                                }
                                return entries.get(index++);
                            }
                        };
                    }
                };
            }
        };
    }

    private static void closeBatches(TextLayout layout) { layout.batches().forEach(TextRenderBatch::close); }

    private static final class CountingUploader implements GlyphAtlasUploader {
        final AtomicInteger uploads = new AtomicInteger();
        final AtomicInteger closed = new AtomicInteger();
        volatile AtlasPixelFormat lastFormat;

        @Override
        public GlyphAtlasUpload upload(AtlasPixels pixels) {
            int sequence = uploads.incrementAndGet();
            lastFormat = pixels.format();
            assertEquals(pixels.width() * pixels.height() * pixels.format().bytesPerPixel(), pixels.data().length);
            return new GlyphAtlasUpload("fake-texture-" + sequence, closed::incrementAndGet);
        }
    }

    private static final class BlockingUploader implements GlyphAtlasUploader {
        final AtomicInteger uploads = new AtomicInteger();
        final AtomicInteger closed = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch interrupted = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override public GlyphAtlasUpload upload(AtlasPixels pixels) {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException error) {
                interrupted.countDown();
                awaitUninterruptibly(release);
            }
            uploads.incrementAndGet();
            return new GlyphAtlasUpload("blocking-texture", closed::incrementAndGet);
        }
    }

    private static final class ThrowingCloseUploader implements GlyphAtlasUploader {
        final AtomicInteger uploads = new AtomicInteger();
        final AtomicInteger closeAttempts = new AtomicInteger();
        final AtomicInteger live = new AtomicInteger();
        volatile boolean throwOnClose;

        @Override public GlyphAtlasUpload upload(AtlasPixels pixels) {
            int sequence = uploads.incrementAndGet();
            live.incrementAndGet();
            return new GlyphAtlasUpload("throwing-texture-" + sequence, () -> {
                closeAttempts.incrementAndGet();
                live.decrementAndGet();
                if (throwOnClose) throw new IllegalStateException("owner-close-" + sequence);
            });
        }
    }

    private static final class BlockingFailUploader implements GlyphAtlasUploader {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override public GlyphAtlasUpload upload(AtlasPixels pixels) {
            entered.countDown();
            awaitUninterruptibly(release);
            throw new IllegalStateException("expected upload failure");
        }
    }
}
