package com.github.slmpc.lumingraphics.ui.tree;

import java.util.List;
import java.util.Objects;

public final class UiNodes {
    private UiNodes() {
    }

    public static <T> T require(T value) {
        return Objects.requireNonNull(value, "UI node value");
    }

    public static List<UiNode> copy(List<UiNode> nodes) {
        require(nodes);
        if (nodes.stream().anyMatch(Objects::isNull)) throw new UiMalformedTreeException("node list contains null");
        return List.copyOf(nodes);
    }

    public static void finite(float... values) {
        for (float value : values)
            if (!Float.isFinite(value)) throw new IllegalArgumentException("UI node value must be finite");
    }

    public static void nonNegative(float... values) {
        finite(values);
        for (float value : values)
            if (value < 0) throw new IllegalArgumentException("UI node value must be non-negative");
    }

    public static void unit(float... values) {
        finite(values);
        for (float value : values)
            if (value < 0 || value > 1) throw new IllegalArgumentException("UI progress must be in 0..1");
    }
}

