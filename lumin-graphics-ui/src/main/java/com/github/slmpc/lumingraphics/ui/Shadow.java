package com.github.slmpc.lumingraphics.ui;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record Shadow(UiRect bounds, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, float blurRadius, LuminColor color) implements UiNode { public Shadow { UiNodes.require(bounds); UiNodes.require(color); UiNodes.nonNegative(radiusTopLeft,radiusTopRight,radiusBottomRight,radiusBottomLeft,blurRadius); } }
