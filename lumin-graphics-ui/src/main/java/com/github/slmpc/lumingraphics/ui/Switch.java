package com.github.slmpc.lumingraphics.ui;
public record Switch(SwitchElement element) implements UiNode { public Switch { UiNodes.require(element); } public Switch(UiRect b,float toggle,float hover){this(new SwitchElement(b,toggle,hover));} }
