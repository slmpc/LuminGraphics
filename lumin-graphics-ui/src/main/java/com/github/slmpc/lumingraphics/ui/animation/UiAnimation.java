package com.github.slmpc.lumingraphics.ui.animation;

public interface UiAnimation {
    float advance(float target);
    boolean active();
}

