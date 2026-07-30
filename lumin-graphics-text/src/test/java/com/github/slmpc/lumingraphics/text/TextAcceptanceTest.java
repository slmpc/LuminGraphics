package com.github.slmpc.lumingraphics.text;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TextAcceptanceTest {
    private static final FontResource FONT = FontResource.classpath("assets/lumin_graphics/fonts/font.ttf");

    @Test
    void iconCharsMapToConcreteBundledIconGlyphs() {
        assertEquals("\ue8b6", IconChars.SEARCH);
        assertEquals("\ue5cd", IconChars.CLOSE);
        try (TtfFontFile icons = TtfFontFile.open(
                FontResource.classpath("assets/lumin_graphics/fonts/icons.ttf"), 48, 4)) {
            assertTrue(icons.hasGlyph(IconChars.SEARCH.codePointAt(0)), "MIG-TEXT-ICON-CHARS");
            assertTrue(icons.rasterize(IconChars.CLOSE.codePointAt(0)).pixels().length > 0);
        }
    }

    @Test
    void bundledResourceManifestMatchesBytesAndLocalOnlyGate() throws Exception {
        Properties manifest = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/assets/lumin_graphics/fonts/manifest.properties")) {
            assertNotNull(input);
            manifest.load(input);
        }
        assertTrue(manifest.getProperty("redistribution").startsWith("local-only"));
        Map<String, Long> expectedSizes = Map.of(
                "font.ttf", 8227312L, "icons.ttf", 1373900L,
                "jura-light.ttf", 154312L, "osakachips.ttf", 24832L);
        for (Map.Entry<String, Long> entry : expectedSizes.entrySet()) {
            byte[] bytes;
            try (InputStream input = getClass().getResourceAsStream("/assets/lumin_graphics/fonts/" + entry.getKey())) {
                assertNotNull(input);
                bytes = input.readAllBytes();
            }
            assertEquals(entry.getValue().longValue(), bytes.length);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            assertEquals(manifest.getProperty(entry.getKey() + ".sha256"), hash);
        }
    }

    @Test
    @org.junit.jupiter.api.DisplayName("MIG-TEXT-RENDERER and MIG-TEXT-TTF-RENDERER sink behavior")
    void rendererSubmitsStableAtlasOrderedBatchesAndRejectsClosedUse() {
        TraceUploader uploader = new TraceUploader();
        List<TextRenderBatch> submitted = new java.util.ArrayList<>();
        try (TtfFontLoader font = new TtfFontLoader(FONT, 48, 4, 96, 96, 4, uploader, Runnable::run);
             TtfTextRenderer renderer = new TtfTextRenderer(submitted::addAll)) {
            TextLayout first = renderer.add("ABC", 0, 0, 1, font);
            renderer.draw();
            assertEquals(first.glyphCount(), submitted.stream().mapToInt(TextRenderBatch::glyphCount).sum());
            assertEquals(first.batches(), submitted);
            renderer.clear();
        }
        TtfTextRenderer closed = new TtfTextRenderer(ignored -> {});
        closed.close();
        assertThrows(FontClosedException.class, closed::draw);
    }

    private static final class TraceUploader implements GlyphAtlasUploader {
        private final AtomicInteger next = new AtomicInteger();
        @Override public GlyphAtlasUpload upload(AtlasPixels pixels) {
            return new GlyphAtlasUpload("trace-" + next.incrementAndGet(), () -> {});
        }
    }
}
