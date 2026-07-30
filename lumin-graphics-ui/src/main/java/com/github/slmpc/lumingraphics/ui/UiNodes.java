package com.github.slmpc.lumingraphics.ui;
import java.util.List;
import java.util.Objects;
final class UiNodes {
    private UiNodes() { }
    static <T> T require(T value) { return Objects.requireNonNull(value, "UI node value"); }
    static List<UiNode> copy(List<UiNode> nodes) { require(nodes); if (nodes.stream().anyMatch(Objects::isNull)) throw new UiMalformedTreeException("node list contains null"); return List.copyOf(nodes); }
    static void finite(float... values) { for (float value : values) if (!Float.isFinite(value)) throw new IllegalArgumentException("UI node value must be finite"); }
    static void nonNegative(float... values) { finite(values); for(float value:values)if(value<0)throw new IllegalArgumentException("UI node value must be non-negative"); }
    static void unit(float... values) { finite(values); for(float value:values)if(value<0||value>1)throw new IllegalArgumentException("UI progress must be in 0..1"); }
}
