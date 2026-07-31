package com.github.slmpc.lumingraphics.text;
import com.github.slmpc.lumingraphics.text.font.FontClosedException;
import com.github.slmpc.lumingraphics.text.font.FontResource;
import com.github.slmpc.lumingraphics.text.ttf.TtfFontFile;
import com.github.slmpc.lumingraphics.text.atlas.AtlasPixels;
import com.github.slmpc.lumingraphics.text.atlas.GlyphAtlasUpload;
import com.github.slmpc.lumingraphics.text.atlas.GlyphAtlasUploader;
import com.github.slmpc.lumingraphics.text.atlas.TtfFontLoader;
import com.github.slmpc.lumingraphics.text.layout.TextLayout;
import com.github.slmpc.lumingraphics.text.layout.TextRenderBatch;
import com.github.slmpc.lumingraphics.text.render.TtfTextRenderer;
import com.github.slmpc.lumingraphics.text.icon.IconChars;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TextAcceptanceTest {
    private static final FontResource FONT = TestFonts.resource("font.ttf");

    @Test
    void iconCharsMapToConcreteCallerSuppliedIconGlyphs() {
        assertEquals("\ue8b6", IconChars.SEARCH);
        assertEquals("\ue5cd", IconChars.CLOSE);
        try (TtfFontFile icons = TtfFontFile.open(
                TestFonts.resource("icons.ttf"), 48, 4)) {
            assertTrue(icons.hasGlyph(IconChars.SEARCH.codePointAt(0)), "MIG-TEXT-ICON-CHARS");
            assertTrue(icons.rasterize(IconChars.CLOSE.codePointAt(0)).pixels().length > 0);
        }
    }

    @Test
    void fontsAreCallerSuppliedRatherThanPackagedResources() throws Exception {
        assertNull(getClass().getResource("/assets/lumin_graphics/fonts/font.ttf"));
        assertTrue(TestFonts.resource("font.ttf").read().length > 0);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("MIG-TEXT-RENDERER and MIG-TEXT-TTF-RENDERER sink behavior")
    void rendererSubmitsStableAtlasOrderedBatchesAndRejectsClosedUse() {
        TraceUploader uploader = new TraceUploader();
        List<TextRenderBatch> submitted = new java.util.ArrayList<>();
        AtomicInteger submittedGlyphs = new AtomicInteger();
        try (TtfFontLoader font = new TtfFontLoader(FONT, 48, 4, 96, 96, 4, uploader, Runnable::run);
             TtfTextRenderer renderer = new TtfTextRenderer(draws -> draws.forEach(draw -> {
                 submitted.addAll(draw.batches());
                 submittedGlyphs.addAndGet(draw.glyphCount());
             }))) {
            TextLayout first = renderer.add("ABC", 0, 0, 1, font);
            renderer.draw();
            assertEquals(first.glyphCount(), submittedGlyphs.get());
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
