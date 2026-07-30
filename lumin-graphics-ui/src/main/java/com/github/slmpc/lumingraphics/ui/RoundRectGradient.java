package com.github.slmpc.lumingraphics.ui;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record RoundRectGradient(UiRect bounds,float radiusTopLeft,float radiusTopRight,float radiusBottomRight,float radiusBottomLeft,LuminColor topLeft,LuminColor bottomLeft,LuminColor bottomRight,LuminColor topRight) implements UiNode { public RoundRectGradient { UiNodes.require(bounds); UiNodes.require(topLeft); UiNodes.require(bottomLeft); UiNodes.require(bottomRight); UiNodes.require(topRight);UiNodes.nonNegative(radiusTopLeft,radiusTopRight,radiusBottomRight,radiusBottomLeft); } }
