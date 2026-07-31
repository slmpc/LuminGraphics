package com.github.slmpc.lumingraphics.ui.control;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;
public record FilledField(UiRect bounds,boolean focused,float hoverProgress) implements UiNode { public FilledField { UiNodes.require(bounds);UiNodes.unit(hoverProgress); } }

