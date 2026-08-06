package com.github.slmpc.lumingraphics.ui.node.primitive;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

public record Texture(String textureId, UiRect bounds, float radiusTopLeft, float radiusTopRight,
                      float radiusBottomRight, float radiusBottomLeft, float u0, float v0, float u1, float v1,
                      LuminColor color) implements UiNode {
    public Texture {
        if (textureId == null || textureId.isBlank()) throw new IllegalArgumentException("texture id is blank");
        UiNodes.require(bounds);
        UiNodes.require(color);
        UiNodes.nonNegative(radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
        UiNodes.unit(u0, v0, u1, v1);
        if (u1 < u0 || v1 < v0) throw new IllegalArgumentException("texture UV range is reversed");
    }
}

