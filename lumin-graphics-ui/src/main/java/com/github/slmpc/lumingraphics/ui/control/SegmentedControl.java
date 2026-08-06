package com.github.slmpc.lumingraphics.ui.control;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;

public record SegmentedControl(UiRect bounds, String leadingLabel, String trailingLabel, float progress,
                               float hoverProgress) implements UiNode {
    public SegmentedControl {
        UiNodes.require(bounds);
        UiNodes.require(leadingLabel);
        UiNodes.require(trailingLabel);
        UiNodes.unit(progress, hoverProgress);
    }
}

