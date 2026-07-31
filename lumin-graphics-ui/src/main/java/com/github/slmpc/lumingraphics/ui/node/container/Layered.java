package com.github.slmpc.lumingraphics.ui.node.container;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import java.util.Objects;
public record Layered(int layer, UiNode child) implements UiNode { public Layered { Objects.requireNonNull(child, "child"); } }

