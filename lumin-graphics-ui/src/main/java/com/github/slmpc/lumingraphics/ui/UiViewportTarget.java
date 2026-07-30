package com.github.slmpc.lumingraphics.ui;
public interface UiViewportTarget { void begin(UiRect viewport); void render(UiTree tree); void queue(UiRect viewport,float scroll,float maxScroll,float contentHeight,int mouseX,int mouseY); }
