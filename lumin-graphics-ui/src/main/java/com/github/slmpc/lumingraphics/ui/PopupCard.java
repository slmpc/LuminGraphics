package com.github.slmpc.lumingraphics.ui;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record PopupCard(UiRect bounds,float radius,float blurRadius,LuminColor shadowColor,LuminColor surfaceColor) implements UiNode { public PopupCard { UiNodes.require(bounds); UiNodes.require(shadowColor); UiNodes.require(surfaceColor); } }
