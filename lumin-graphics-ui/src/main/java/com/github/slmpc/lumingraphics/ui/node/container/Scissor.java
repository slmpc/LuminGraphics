package com.github.slmpc.lumingraphics.ui.node.container;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;
import java.util.List;
public record Scissor(UiRect clip, List<UiNode> children) implements UiNode { public Scissor { UiNodes.require(clip); children = UiNodes.copy(children); } }

