package com.github.slmpc.lumingraphics.text;
import com.github.slmpc.lumingraphics.text.font.FontClosedException;
import com.github.slmpc.lumingraphics.text.font.FontResource;
import com.github.slmpc.lumingraphics.text.ttf.TtfFontFile;
import com.github.slmpc.lumingraphics.text.atlas.GlyphAtlasUpload;
import com.github.slmpc.lumingraphics.text.atlas.GlyphAtlasUploader;
import com.github.slmpc.lumingraphics.text.atlas.TtfFontLoader;
import com.github.slmpc.lumingraphics.text.layout.TextLayout;
import com.github.slmpc.lumingraphics.text.layout.TextLayoutEngine;
import com.github.slmpc.lumingraphics.text.layout.TextRenderBatch;
import com.github.slmpc.lumingraphics.text.emoji.EmojiGlyph;
import com.github.slmpc.lumingraphics.text.emoji.SystemEmojiAtlas;
import com.github.slmpc.lumingraphics.text.icon.IconChars;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TextConsumerFixtureTest {
    private static final String ROOT = "assets/lumin_graphics/fonts/";

    @Test
    void printsStableConsumerTraceAcrossFontsIconAndEmojiFallback() {
        List<String> ownership = new ArrayList<>();
        AtomicInteger sequence = new AtomicInteger();
        GlyphAtlasUploader textUploader = uploader("text", sequence, ownership);
        GlyphAtlasUploader iconUploader = uploader("icon", sequence, ownership);
        GlyphAtlasUploader emojiUploader = uploader("emoji", sequence, ownership);

        List<String> fontMetrics = new ArrayList<>();
        boolean defaultSupportsEmoji;
        for (String name : List.of("font.ttf", "icons.ttf", "jura-light.ttf", "osakachips.ttf")) {
            try (TtfFontFile file = TtfFontFile.open(FontResource.classpath(ROOT + name), 48, 4)) {
                fontMetrics.add(name + "=" + file.metrics().ascent() + "/" + file.metrics().lineHeight()
                        + "/" + file.advance('A'));
                if (name.equals("font.ttf")) {
                    defaultSupportsEmoji = file.hasGlyph(0x1F642);
                    assertFalse(defaultSupportsEmoji, "fixture codepoint must exercise emoji fallback");
                }
            }
        }

        TtfFontLoader text = new TtfFontLoader(FontResource.classpath(ROOT + "font.ttf"),
                48, 4, 128, 128, 2, textUploader, Runnable::run);
        TtfFontLoader icons = new TtfFontLoader(FontResource.classpath(ROOT + "icons.ttf"),
                48, 4, 128, 128, 2, iconUploader, Runnable::run);
        SystemEmojiAtlas emoji = new SystemEmojiAtlas(64, 64, 24, emojiUploader);
        TextLayoutEngine engine = new TextLayoutEngine();
        TextLayout kerned = engine.layout("AV", 2, 4, 0.75f, text);
        TextLayout icon = engine.layout(IconChars.SEARCH, 40, 4, 0.75f, icons);
        EmojiGlyph fallback = emoji.require(0x1F642);
        long combinedHash = kerned.stableHash() ^ Long.rotateLeft(icon.stableHash(), 17)
                ^ ((long) fallback.codepoint() << 32);

        assertEquals(List.of("font.ttf=32/40/23", "icons.ttf=37/40/33",
                "jura-light.ttf=33/40/20", "osakachips.ttf=34/40/17"), fontMetrics);
        assertEquals(4, kerned.glyphCount() + icon.glyphCount() + 1);
        assertEquals(3, kerned.batches().size() + icon.batches().size() + 1);
        assertEquals(0xd58643bdee2f5b82L, combinedHash);

        System.out.println("fixedSequence=AV+U+E8B6+U+1F642(fallback)");
        System.out.println("fonts=" + String.join("|", fontMetrics));
        System.out.printf("layout=%d,%d,%.3f,%.3f,%016x%n",
                kerned.glyphCount() + icon.glyphCount() + 1,
                kerned.batches().size() + icon.batches().size() + 1,
                kerned.width() + icon.width() + fallback.width(),
                Math.max(kerned.height(), fallback.height()), combinedHash);
        GlyphAtlasUpload retainedText = kerned.batches().get(0).upload();
        GlyphAtlasUpload retainedIcon = icon.batches().get(0).upload();
        emoji.close();
        icons.close();
        text.close();
        assertFalse(retainedText.isClosed(), "layout batch must retain the text upload after loader close");
        assertFalse(retainedIcon.isClosed(), "layout batch must retain the icon upload after loader close");
        icon.batches().forEach(TextRenderBatch::close);
        kerned.batches().forEach(TextRenderBatch::close);
        assertEquals(List.of(
                "upload:text-1@r1:128x128", "upload:text-2@r2:128x128", "close:text-1@r1",
                "upload:icon-3@r1:128x128", "upload:emoji-4@r1:64x64", "close:emoji-4@r1",
                "close:icon-3@r1", "close:text-2@r2"), ownership);
        FontClosedException closed = assertThrows(FontClosedException.class, () -> text.requireGlyph('Z'));
        System.out.println("uploadTrace=" + String.join("|", ownership));
        System.out.println("postClose=" + closed.getClass().getSimpleName() + ":" + closed.getMessage());
    }

    private static GlyphAtlasUploader uploader(String name, AtomicInteger sequence, List<String> trace) {
        return pixels -> {
            String id = name + "-" + sequence.incrementAndGet() + "@r" + pixels.revision();
            trace.add("upload:" + id + ":" + pixels.width() + "x" + pixels.height());
            return new GlyphAtlasUpload(id, () -> trace.add("close:" + id));
        };
    }
}
