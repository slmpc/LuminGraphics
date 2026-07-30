package com.github.slmpc.lumingraphics.render.scheduler;

import com.github.slmpc.prismrhi.rendering.RhiRect2D;

public record Render2DScissor(int x, int y, int width, int height) {
    public Render2DScissor {
        if (x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("scissor values must be non-negative and visible");
        }
    }
    public Render2DScissor intersect(Render2DScissor other) {
        int left = Math.max(x, other.x);
        int top = Math.max(y, other.y);
        int right = Math.min(Math.addExact(x, width), Math.addExact(other.x, other.width));
        int bottom = Math.min(Math.addExact(y, height), Math.addExact(other.y, other.height));
        if (right <= left || bottom <= top) throw new IllegalArgumentException("nested scissors do not intersect");
        return new Render2DScissor(left, top, right - left, bottom - top);
    }
    public RhiRect2D toRhi() { return RhiRect2D.of(x, y, width, height); }
}
