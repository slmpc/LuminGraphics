package com.github.slmpc.lumingraphics.ui;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record Rect(UiRect bounds, LuminColor color) implements UiNode { public Rect { UiNodes.require(bounds); UiNodes.require(color); } }
