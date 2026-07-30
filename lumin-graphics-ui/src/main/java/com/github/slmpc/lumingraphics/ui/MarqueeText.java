package com.github.slmpc.lumingraphics.ui;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record MarqueeText(String text,float x,float y,float scale,LuminColor color,String fontId,UiRect clip) implements UiNode { public MarqueeText { UiNodes.require(text); UiNodes.require(color); UiNodes.require(clip); } }
