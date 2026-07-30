package com.github.slmpc.lumingraphics.text;

import java.util.List;

@FunctionalInterface
public interface TextBatchSink {
    void draw(List<TextRenderBatch> batches);
}
