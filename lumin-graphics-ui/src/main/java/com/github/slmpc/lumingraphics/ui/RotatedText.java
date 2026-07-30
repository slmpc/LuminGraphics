package com.github.slmpc.lumingraphics.ui;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record RotatedText(String text,float x,float y,float scale,LuminColor color,String fontId,float originX,float originY,float rotationDegrees) implements UiNode { public RotatedText { UiNodes.require(text); UiNodes.require(color); UiNodes.finite(x,y,scale,originX,originY,rotationDegrees); } }
