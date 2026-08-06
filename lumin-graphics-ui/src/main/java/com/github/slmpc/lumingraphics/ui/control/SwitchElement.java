package com.github.slmpc.lumingraphics.ui.control;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;

public record SwitchElement(UiRect bounds, float toggleProgress, float hoverProgress) {
    public SwitchElement {
        UiNodes.require(bounds);
        UiNodes.unit(toggleProgress, hoverProgress);
    }
}

