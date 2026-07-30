package com.github.slmpc.lumingraphics.ui;
import java.util.List;
public record Layer(int layer, List<UiNode> children) implements UiNode { public Layer { children = UiNodes.copy(children); } }
