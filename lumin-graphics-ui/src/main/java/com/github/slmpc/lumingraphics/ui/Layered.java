package com.github.slmpc.lumingraphics.ui;
import java.util.Objects;
public record Layered(int layer, UiNode child) implements UiNode { public Layered { Objects.requireNonNull(child, "child"); } }
