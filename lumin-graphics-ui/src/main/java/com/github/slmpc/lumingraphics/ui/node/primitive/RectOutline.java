package com.github.slmpc.lumingraphics.ui.node.primitive;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

public record RectOutline(UiRect bounds, float outlineWidth, LuminColor color) implements UiNode {
    public RectOutline {
        UiNodes.require(bounds);
        UiNodes.require(color);
        UiNodes.nonNegative(outlineWidth);
    }
}

