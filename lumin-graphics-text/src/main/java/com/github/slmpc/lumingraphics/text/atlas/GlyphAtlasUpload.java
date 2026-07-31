package com.github.slmpc.lumingraphics.text.atlas;
import com.github.slmpc.lumingraphics.text.font.FontClosedException;
import com.github.slmpc.lumingraphics.text.font.FontException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GlyphAtlasUpload implements AutoCloseable {
    private final Object texture;
    private final AutoCloseable owner;
    private final AtomicBoolean closed = new AtomicBoolean();

    public GlyphAtlasUpload(Object texture, AutoCloseable owner) {
        this.texture = Objects.requireNonNull(texture, "texture");
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public Object texture() {
        if (closed.get()) throw new FontClosedException("Glyph atlas upload is closed");
        return texture;
    }

    public boolean isClosed() { return closed.get(); }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            try { owner.close(); } catch (Exception error) { throw new FontException("Failed to close glyph upload", error); }
        }
    }
}
