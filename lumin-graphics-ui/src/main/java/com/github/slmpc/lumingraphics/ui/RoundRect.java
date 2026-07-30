package com.github.slmpc.lumingraphics.ui;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record RoundRect(UiRect bounds, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, LuminColor color) implements UiNode { public RoundRect { UiNodes.require(bounds); UiNodes.require(color); UiNodes.nonNegative(radiusTopLeft,radiusTopRight,radiusBottomRight,radiusBottomLeft); } public RoundRect(UiRect b,float r,LuminColor c){this(b,r,r,r,r,c);} }
