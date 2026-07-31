package com.github.slmpc.lumingraphics.ui.node.primitive;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record Outline(UiRect bounds,float radiusTopLeft,float radiusTopRight,float radiusBottomRight,float radiusBottomLeft,float outlineWidth,LuminColor color) implements UiNode { public Outline { UiNodes.require(bounds); UiNodes.require(color);UiNodes.nonNegative(radiusTopLeft,radiusTopRight,radiusBottomRight,radiusBottomLeft,outlineWidth); } public Outline(UiRect b,float r,float w,LuminColor c){this(b,r,r,r,r,w,c);} }

