package com.github.slmpc.lumingraphics.ui.viewport;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;

public interface UiViewportTarget {
    void begin(UiRect viewport);

    void render(UiTree tree);

    void queue(UiRect viewport, float scroll, float maxScroll, float contentHeight, int mouseX, int mouseY);
}

