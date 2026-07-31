package com.github.slmpc.lumingraphics.ui.node.primitive;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record Triangle(float centerX,float centerY,float size,float progress,LuminColor color) implements UiNode { public Triangle { UiNodes.require(color); UiNodes.finite(centerX,centerY);UiNodes.nonNegative(size);UiNodes.unit(progress); } }

