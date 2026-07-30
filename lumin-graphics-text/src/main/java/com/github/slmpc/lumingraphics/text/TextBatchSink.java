package com.github.slmpc.lumingraphics.text;

import java.util.List;

@FunctionalInterface
public interface TextBatchSink {
    /**
     * Borrows the ordered draws and their batches only until this method returns or throws.
     * A delayed consumer must retain each draw during this call and close its retained copy after flush or clear.
     */
    void draw(List<TextDraw> draws);
}
