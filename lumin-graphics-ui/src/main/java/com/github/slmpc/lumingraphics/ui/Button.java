package com.github.slmpc.lumingraphics.ui;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record Button(ButtonElement element) implements UiNode { public Button { UiNodes.require(element); } public Button(UiRect b,float r,LuminColor bg,String label,float scale,LuminColor labelColor){this(new ButtonElement(b,r,bg,label,scale,labelColor));} }
