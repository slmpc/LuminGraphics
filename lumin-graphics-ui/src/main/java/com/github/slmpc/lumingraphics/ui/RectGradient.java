package com.github.slmpc.lumingraphics.ui;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record RectGradient(UiRect bounds,LuminColor topLeft,LuminColor bottomLeft,LuminColor bottomRight,LuminColor topRight) implements UiNode { public RectGradient { UiNodes.require(bounds); UiNodes.require(topLeft); UiNodes.require(bottomLeft); UiNodes.require(bottomRight); UiNodes.require(topRight); } }
