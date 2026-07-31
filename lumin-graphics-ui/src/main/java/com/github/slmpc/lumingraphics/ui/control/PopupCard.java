package com.github.slmpc.lumingraphics.ui.control;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record PopupCard(UiRect bounds,float radius,float blurRadius,LuminColor shadowColor,LuminColor surfaceColor) implements UiNode { public PopupCard { UiNodes.require(bounds); UiNodes.require(shadowColor); UiNodes.require(surfaceColor); } }

