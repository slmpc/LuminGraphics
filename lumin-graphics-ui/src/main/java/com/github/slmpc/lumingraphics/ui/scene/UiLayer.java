package com.github.slmpc.lumingraphics.ui.scene;

public enum UiLayer {
    BACKGROUND(0), CHROME(100), CONTENT(200), FLOATING(300), POPUP(400), OVERLAY(500);
    private final int baseLayer;

    UiLayer(int value) {
        baseLayer = value;
    }

    public int baseLayer() {
        return baseLayer;
    }
}

