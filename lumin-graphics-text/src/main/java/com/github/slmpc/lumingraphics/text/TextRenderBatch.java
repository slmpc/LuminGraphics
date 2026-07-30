package com.github.slmpc.lumingraphics.text;

import java.util.List;
import java.util.Objects;

public final class TextRenderBatch implements AutoCloseable {
    private final TtfGlyphAtlas atlas;
    private final List<GlyphPlacement> glyphs;
    private final TtfGlyphAtlas.UploadLease uploadLease;

    public TextRenderBatch(TtfGlyphAtlas atlas, List<GlyphPlacement> glyphs) {
        this.atlas = Objects.requireNonNull(atlas, "atlas");
        this.glyphs = List.copyOf(glyphs);
        uploadLease = atlas.retainUpload();
    }
    public TtfGlyphAtlas atlas() { return atlas; }
    public List<GlyphPlacement> glyphs() { return glyphs; }
    public int glyphCount() { return glyphs.size(); }
    public GlyphAtlasUpload upload() { return uploadLease.upload(); }
    @Override public void close() { uploadLease.close(); }

    @Override public boolean equals(Object other) {
        return other instanceof TextRenderBatch batch && atlas == batch.atlas
                && upload() == batch.upload() && glyphs.equals(batch.glyphs);
    }
    @Override public int hashCode() {
        return 31 * (31 * System.identityHashCode(atlas) + System.identityHashCode(upload())) + glyphs.hashCode();
    }
    @Override public String toString() { return "TextRenderBatch[atlas=" + atlas + ", glyphs=" + glyphs + "]"; }
}
