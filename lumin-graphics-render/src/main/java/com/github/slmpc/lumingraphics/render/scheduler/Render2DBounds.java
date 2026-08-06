package com.github.slmpc.lumingraphics.render.scheduler;

public record Render2DBounds(float x, float y, float width, float height) {
    public Render2DBounds {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)
                || width < 0 || height < 0 || !Float.isFinite(x + width) || !Float.isFinite(y + height)) {
            throw new IllegalArgumentException("render bounds must be finite and non-negative");
        }
    }

    public float right() {
        return x + width;
    }

    public float bottom() {
        return y + height;
    }

    public boolean intersects(Render2DBounds other) {
        return right() > other.x && x < other.right() && bottom() > other.y && y < other.bottom();
    }

    public Render2DBounds expand(float amount) {
        float pad = Math.max(0, amount);
        return new Render2DBounds(x - pad, y - pad, width + pad * 2, height + pad * 2);
    }
}
