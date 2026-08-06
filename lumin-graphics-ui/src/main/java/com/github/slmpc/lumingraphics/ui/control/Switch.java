package com.github.slmpc.lumingraphics.ui.control;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;

public record Switch(SwitchElement element) implements UiNode {
    public Switch {
        UiNodes.require(element);
    }

    public Switch(UiRect b, float toggle, float hover) {
        this(new SwitchElement(b, toggle, hover));
    }
}

