package com.github.slmpc.lumingraphics.ui.viewport;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;

import java.util.List;

public record Viewport(UiViewportTarget buffer, UiRect viewport, float scroll, float maxScroll, float contentHeight,
                       int mouseX, int mouseY, List<UiNode> children) implements UiNode {
    public Viewport {
        UiNodes.require(buffer);
        UiNodes.require(viewport);
        children = UiNodes.copy(children);
    }
}

