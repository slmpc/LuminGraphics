package com.github.slmpc.lumingraphics.ui;
public record SegmentedControl(UiRect bounds,String leadingLabel,String trailingLabel,float progress,float hoverProgress) implements UiNode { public SegmentedControl { UiNodes.require(bounds); UiNodes.require(leadingLabel); UiNodes.require(trailingLabel);UiNodes.unit(progress,hoverProgress); } }
