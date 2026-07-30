package com.github.slmpc.lumingraphics.ui;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record AssistChip(UiRect bounds,String label,float textScale,LuminColor background,LuminColor foreground,String trailingIcon,float trailingIconScale,String trailingIconFontId) implements UiNode { public AssistChip { UiNodes.require(bounds); UiNodes.require(label); UiNodes.require(background); UiNodes.require(foreground); } }
