package com.github.slmpc.lumingraphics.ui.control;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

public record Slider(UiRect bounds, float progress, float trackRadius, LuminColor trackColor, float activeEndInset,
                     float activeMinWidth, LuminColor activeColor, float handleWidth, float handleHeight,
                     float handleRadius, LuminColor handleColor) implements UiNode {
    public Slider {
        UiNodes.require(bounds);
        UiNodes.require(trackColor);
        UiNodes.require(activeColor);
        UiNodes.require(handleColor);
        UiNodes.unit(progress);
        UiNodes.nonNegative(trackRadius, activeEndInset, activeMinWidth, handleWidth, handleHeight, handleRadius);
    }
}

