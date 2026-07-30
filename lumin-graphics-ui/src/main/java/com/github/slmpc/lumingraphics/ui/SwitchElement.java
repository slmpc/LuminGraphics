package com.github.slmpc.lumingraphics.ui;
public record SwitchElement(UiRect bounds, float toggleProgress, float hoverProgress) { public SwitchElement { UiNodes.require(bounds);UiNodes.unit(toggleProgress,hoverProgress); } }
