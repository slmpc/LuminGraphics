package com.github.slmpc.lumingraphics.ui.control;

public record SelectionRange(int start, int end) {
    public SelectionRange {
        if (start < 0 || end < start) throw new IllegalArgumentException("selection is invalid");
    }
}

