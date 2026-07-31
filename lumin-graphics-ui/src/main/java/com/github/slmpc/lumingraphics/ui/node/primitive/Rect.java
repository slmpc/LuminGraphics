package com.github.slmpc.lumingraphics.ui.node.primitive;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record Rect(UiRect bounds, LuminColor color) implements UiNode { public Rect { UiNodes.require(bounds); UiNodes.require(color); } }

