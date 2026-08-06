package com.github.slmpc.lumingraphics.ui.control;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

public record Button(ButtonElement element) implements UiNode {
    public Button {
        UiNodes.require(element);
    }

    public Button(UiRect b, float r, LuminColor bg, String label, float scale, LuminColor labelColor) {
        this(new ButtonElement(b, r, bg, label, scale, labelColor));
    }
}

