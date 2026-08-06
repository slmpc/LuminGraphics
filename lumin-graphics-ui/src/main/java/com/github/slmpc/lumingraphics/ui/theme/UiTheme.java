package com.github.slmpc.lumingraphics.ui.theme;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

public interface UiTheme {
    float controlRadius();

    LuminColor textPrimary();

    LuminColor textMuted();

    LuminColor outlineSoft();

    LuminColor surface();

    LuminColor accent();

    long hoverAnimationDuration();

    boolean light();

    default LuminColor withAlpha(LuminColor color, float alpha) {
        return new LuminColor(color.red(), color.green(), color.blue(), Math.max(0, Math.min(1, alpha)));
    }

    default LuminColor lerp(LuminColor a, LuminColor b, float delta) {
        float d = Math.max(0, Math.min(1, delta));
        return new LuminColor(a.red() + (b.red() - a.red()) * d, a.green() + (b.green() - a.green()) * d,
                a.blue() + (b.blue() - a.blue()) * d, a.alpha() + (b.alpha() - a.alpha()) * d);
    }

    default LuminColor scrollBar(float hoverProgress) {
        return withAlpha(textMuted(), 0.55f + 0.35f * hoverProgress);
    }

    default LuminColor stateLayer(LuminColor color, float progress, int maxAlpha) {
        return withAlpha(color, Math.max(0, Math.min(1, progress)) * Math.max(0, Math.min(255, maxAlpha)) / 255f);
    }

    default LuminColor filledFieldSurface(boolean focused, float hoverProgress) {
        return focused ? lerp(surface(), accent(), light() ? .58f : .42f) : lerp(surface(), outlineSoft(), Math.max(0, Math.min(1, hoverProgress * .35f)));
    }

    default LuminColor segmentedControlSurface() {
        return surface();
    }

    default LuminColor segmentedControlIndicator() {
        return withAlpha(accent(), .3f);
    }

    default LuminColor segmentedControlActiveLabel() {
        return textPrimary();
    }

    default LuminColor segmentedControlInactiveLabel() {
        return textMuted();
    }

    default LuminColor switchTrack(float progress) {
        return lerp(surface(), accent(), progress);
    }

    default LuminColor switchKnob(float progress) {
        return lerp(textMuted(), textPrimary(), progress);
    }

    default LuminColor switchTrackOutline(float progress, float hover) {
        return withAlpha(lerp(outlineSoft(), textPrimary(), hover * .35f), (1 - Math.max(0, Math.min(1, progress))) * (light() ? .74f : .66f));
    }

    default float switchTrackOutlineWidth(float progress) {
        return 1 + (1 - Math.max(0, Math.min(1, progress))) * .1f;
    }

    default float switchHandleSizeOff() {
        return 8;
    }

    default float switchHandleSizeOn() {
        return 12;
    }

    default float switchHandleInsetOff() {
        return 4;
    }

    default float switchHandleInsetOn() {
        return 2;
    }

    default float switchStateLayerSize() {
        return 20;
    }

    static UiTheme defaults() {
        return new Basic(4, new LuminColor(.94f, .95f, .97f, 1), new LuminColor(.58f, .61f, .66f, 1),
                new LuminColor(.3f, .33f, .38f, 1), new LuminColor(.09f, .1f, .12f, 1),
                new LuminColor(.2f, .55f, .95f, 1), 120, false);
    }

    record Basic(float controlRadius, LuminColor textPrimary, LuminColor textMuted, LuminColor outlineSoft,
                 LuminColor surface, LuminColor accent, long hoverAnimationDuration, boolean light) implements UiTheme {
        public Basic {
            if (controlRadius < 0 || hoverAnimationDuration < 0)
                throw new IllegalArgumentException("theme values are invalid");
        }
    }
}

