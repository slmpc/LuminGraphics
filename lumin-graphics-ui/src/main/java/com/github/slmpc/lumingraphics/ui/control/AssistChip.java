package com.github.slmpc.lumingraphics.ui.control;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

public record AssistChip(UiRect bounds, String label, float textScale, LuminColor background, LuminColor foreground,
                         String trailingIcon, float trailingIconScale, String trailingIconFontId) implements UiNode {
    public AssistChip {
        UiNodes.require(bounds);
        UiNodes.require(label);
        UiNodes.require(background);
        UiNodes.require(foreground);
    }
}

