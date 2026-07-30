package com.github.slmpc.lumingraphics.ui;
public record Input(InputElement element) implements UiNode { public Input { UiNodes.require(element); } }
