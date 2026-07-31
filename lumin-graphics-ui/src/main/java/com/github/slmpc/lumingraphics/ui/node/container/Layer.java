package com.github.slmpc.lumingraphics.ui.node.container;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;
import java.util.List;
public record Layer(int layer, List<UiNode> children) implements UiNode { public Layer { children = UiNodes.copy(children); } }

