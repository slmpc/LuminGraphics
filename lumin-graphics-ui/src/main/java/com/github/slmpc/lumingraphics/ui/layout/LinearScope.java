package com.github.slmpc.lumingraphics.ui.layout;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;

import java.util.function.Consumer;

public final class LinearScope {
    private final UiTree.Scope draw;
    private final UiRect bounds;
    private final Axis axis;
    private final float gap;
    private float cursor;

    LinearScope(UiTree.Scope draw, UiRect bounds, Axis axis, float gap) {
        this.draw = draw;
        this.bounds = bounds;
        this.axis = axis;
        this.gap = gap;
        this.cursor = axis == Axis.VERTICAL ? bounds.y() : bounds.x();
    }

    public void item(float size, Consumer<LayoutScope> content) {
        float actual = Math.max(0, size);
        UiRect rect = axis == Axis.VERTICAL ? new UiRect(bounds.x(), cursor, bounds.width(), actual) : new UiRect(cursor, bounds.y(), actual, bounds.height());
        content.accept(new LayoutScope(draw, rect));
        cursor += actual + gap;
    }

    public void fill(Consumer<LayoutScope> content) {
        float end = axis == Axis.VERTICAL ? bounds.bottom() : bounds.right();
        item(Math.max(0, end - cursor), content);
    }

    public void spacer(float size) {
        cursor += Math.max(0, size) + gap;
    }

    public float cursor() {
        return cursor;
    }
}

