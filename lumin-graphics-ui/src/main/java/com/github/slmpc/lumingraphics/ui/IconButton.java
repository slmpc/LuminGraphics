package com.github.slmpc.lumingraphics.ui;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record IconButton(UiRect bounds,String label,float scale,LuminColor tone,float hoverProgress) implements UiNode { public IconButton { UiNodes.require(bounds); UiNodes.require(label); UiNodes.require(tone);UiNodes.nonNegative(scale);UiNodes.unit(hoverProgress); } }
