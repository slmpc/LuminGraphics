package com.github.slmpc.lumingraphics.ui.node.primitive;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

public record MarqueeText(String text, float x, float y, float scale, LuminColor color, String fontId,
                          UiRect clip) implements UiNode {
    public MarqueeText {
        UiNodes.require(text);
        UiNodes.require(color);
        UiNodes.require(clip);
    }
}

