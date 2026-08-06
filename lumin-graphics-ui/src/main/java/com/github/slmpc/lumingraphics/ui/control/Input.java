package com.github.slmpc.lumingraphics.ui.control;

import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;

public record Input(InputElement element) implements UiNode {
    public Input {
        UiNodes.require(element);
    }
}

