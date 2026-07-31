package com.github.slmpc.lumingraphics.text.layout;
import com.github.slmpc.lumingraphics.text.font.FontClosedException;
import com.github.slmpc.lumingraphics.text.atlas.GlyphAtlasUpload;
import com.github.slmpc.lumingraphics.text.atlas.GlyphAtlasUploadLease;
import com.github.slmpc.lumingraphics.text.atlas.TtfGlyphAtlas;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TextRenderBatch implements AutoCloseable {
    private final TtfGlyphAtlas atlas;
    private final List<GlyphPlacement> glyphs;
    private final GlyphAtlasUploadLease uploadLease;
    private final AtomicBoolean closed = new AtomicBoolean();

    public TextRenderBatch(TtfGlyphAtlas atlas, List<GlyphPlacement> glyphs) {
        this.atlas = Objects.requireNonNull(atlas, "atlas");
        this.glyphs = List.copyOf(glyphs);
        uploadLease = atlas.retainUpload();
    }
    public TtfGlyphAtlas atlas() { ensureOpen(); return atlas; }
    public List<GlyphPlacement> glyphs() { ensureOpen(); return glyphs; }
    public int glyphCount() { ensureOpen(); return glyphs.size(); }
    public GlyphAtlasUpload upload() { ensureOpen(); return uploadLease.upload(); }
    public boolean isClosed() { return closed.get(); }
    public synchronized TextRenderBatch retain() {
        ensureOpen();
        return new TextRenderBatch(atlas, glyphs, uploadLease.retain());
    }
    @Override public synchronized void close() {
        if (closed.compareAndSet(false, true)) uploadLease.close();
    }

    private TextRenderBatch(TtfGlyphAtlas atlas, List<GlyphPlacement> glyphs,
                            GlyphAtlasUploadLease uploadLease) {
        this.atlas = atlas;
        this.glyphs = List.copyOf(glyphs);
        this.uploadLease = uploadLease;
    }

    private void ensureOpen() {
        if (closed.get()) throw new FontClosedException("Text render batch is closed");
    }

    @Override public boolean equals(Object other) {
        return other instanceof TextRenderBatch batch && atlas == batch.atlas
                && uploadLease.upload() == batch.uploadLease.upload() && glyphs.equals(batch.glyphs);
    }
    @Override public int hashCode() {
        return 31 * (31 * System.identityHashCode(atlas) + System.identityHashCode(uploadLease.upload())) + glyphs.hashCode();
    }
    @Override public String toString() { return "TextRenderBatch[atlas=" + atlas + ", glyphs=" + glyphs + "]"; }
}
