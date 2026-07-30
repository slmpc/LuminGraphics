package com.github.slmpc.lumingraphics.ui;
public record FilledField(UiRect bounds,boolean focused,float hoverProgress) implements UiNode { public FilledField { UiNodes.require(bounds);UiNodes.unit(hoverProgress); } }
