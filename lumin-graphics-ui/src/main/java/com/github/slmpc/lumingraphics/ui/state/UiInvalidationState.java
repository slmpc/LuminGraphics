package com.github.slmpc.lumingraphics.ui.state;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;

public final class UiInvalidationState {
    private boolean dirty = true, animations;
    private int mouseX = Integer.MIN_VALUE, mouseY = Integer.MIN_VALUE, guiHeight = -1;
    private long signature = Long.MIN_VALUE;
    private UiRect bounds;

    public void markDirty() {
        dirty = true;
    }

    public void beginRebuild() {
        animations = false;
    }

    public void noteAnimation(boolean active) {
        animations |= active;
    }

    public boolean hasActiveAnimations() {
        return animations;
    }

    public boolean needsRebuild(UiRect value, int x, int y, int height) {
        return needsRebuild(value, x, y, height, 0);
    }

    public boolean needsRebuild(UiRect value, int x, int y, int height, long sig) {
        return dirty || animations || !value.equals(bounds) || x != mouseX || y != mouseY || height != guiHeight || sig != signature;
    }

    public void rememberSnapshot(UiRect value, int x, int y, int height) {
        rememberSnapshot(value, x, y, height, 0);
    }

    public void rememberSnapshot(UiRect value, int x, int y, int height, long sig) {
        bounds = value;
        mouseX = x;
        mouseY = y;
        guiHeight = height;
        signature = sig;
        dirty = false;
    }
}

