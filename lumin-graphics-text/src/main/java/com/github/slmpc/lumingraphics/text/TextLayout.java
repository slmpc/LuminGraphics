package com.github.slmpc.lumingraphics.text;

import java.util.List;

public record TextLayout(float width, float height, int glyphCount, long glyphRevision, long atlasRevision,
                         long stableHash, List<TextRenderBatch> batches) {
    public TextLayout { batches = List.copyOf(batches); }
}
