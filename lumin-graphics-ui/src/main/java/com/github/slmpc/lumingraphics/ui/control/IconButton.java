package com.github.slmpc.lumingraphics.ui.control;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

public record IconButton(UiRect bounds, String label, float scale, LuminColor tone,
                         float hoverProgress) implements UiNode {
    public IconButton {
        UiNodes.require(bounds);
        UiNodes.require(label);
        UiNodes.require(tone);
        UiNodes.nonNegative(scale);
        UiNodes.unit(hoverProgress);
    }
}

