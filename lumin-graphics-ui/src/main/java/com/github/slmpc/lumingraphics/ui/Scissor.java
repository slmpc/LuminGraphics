package com.github.slmpc.lumingraphics.ui;
import java.util.List;
public record Scissor(UiRect clip, List<UiNode> children) implements UiNode { public Scissor { UiNodes.require(clip); children = UiNodes.copy(children); } }
