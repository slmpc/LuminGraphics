package com.github.slmpc.lumingraphics.ui;
public record SelectionRange(int start, int end) { public SelectionRange { if (start < 0 || end < start) throw new IllegalArgumentException("selection is invalid"); } }
