package com.github.slmpc.lumingraphics.ui;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record Text(String text,float x,float y,float scale,LuminColor color,String fontId) implements UiNode { public Text { UiNodes.require(text); UiNodes.require(color); UiNodes.finite(x,y,scale); if(scale<=0) throw new IllegalArgumentException("text scale must be positive"); } }
