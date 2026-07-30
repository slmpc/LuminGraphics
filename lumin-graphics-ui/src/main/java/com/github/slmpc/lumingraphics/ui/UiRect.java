package com.github.slmpc.lumingraphics.ui;

public record UiRect(float x, float y, float width, float height) {
    public UiRect {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)
                || width < 0 || height < 0) throw new IllegalArgumentException("UI rectangle must be finite and non-negative");
    }
    public float right() { return x + width; }
    public float bottom() { return y + height; }
    public float centerX() { return x + width / 2; }
    public float centerY() { return y + height / 2; }
    public boolean contains(double px, double py) { return px >= x && px <= right() && py >= y && py <= bottom(); }
    public UiRect inset(float amount) { return inset(amount, amount); }
    public UiRect inset(float horizontal, float vertical) {
        float h = Math.max(0, Math.min(horizontal, width / 2));
        float v = Math.max(0, Math.min(vertical, height / 2));
        return new UiRect(x + h, y + v, width - h * 2, height - v * 2);
    }
    public UiRect atOrigin() { return new UiRect(0, 0, width, height); }
    public UiRect relativeTo(UiRect origin) { return new UiRect(x - origin.x, y - origin.y, width, height); }
    public UiRect translate(float dx, float dy) { return new UiRect(x + dx, y + dy, width, height); }
    public UiRect intersect(UiRect other) {
        float left = Math.max(x, other.x), top = Math.max(y, other.y);
        float right = Math.min(right(), other.right()), bottom = Math.min(bottom(), other.bottom());
        return right <= left || bottom <= top ? null : new UiRect(left, top, right - left, bottom - top);
    }
}
