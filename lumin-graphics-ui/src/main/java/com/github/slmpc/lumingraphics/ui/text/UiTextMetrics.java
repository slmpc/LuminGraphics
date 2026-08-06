package com.github.slmpc.lumingraphics.ui.text;

import com.github.slmpc.lumingraphics.ui.resource.UiResourceResolver;

import com.github.slmpc.lumingraphics.text.render.TextRenderer;
import com.github.slmpc.lumingraphics.text.atlas.TtfFontLoader;

import java.util.Objects;

@FunctionalInterface
public interface UiTextMetrics {
    Measurement measure(String text, float scale, String fontId);

    default float textWidth(String text, float scale, String fontId) {
        return measure(text, scale, fontId).width();
    }

    default float textHeight(float scale, String fontId) {
        return measure("Mg", scale, fontId).height();
    }

    static UiTextMetrics of(UiTextMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics");
        return (text, scale, fontId) -> {
            if (text == null || !Float.isFinite(scale) || scale <= 0)
                throw new IllegalArgumentException("text metric input is invalid");
            return Objects.requireNonNull(metrics.measure(text, scale, fontId), "measurement");
        };
    }

    static UiTextMetrics renderer(TextRenderer renderer, UiResourceResolver resources) {
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(resources, "resources");
        return of((text, scale, fontId) -> {
            TtfFontLoader font = resources.font(fontId);
            var measured = renderer.measure(text, scale, font);
            return new Measurement(measured.width(), measured.height());
        });
    }

    record Measurement(float width, float height) {
        public Measurement {
            if (!Float.isFinite(width) || !Float.isFinite(height) || width < 0 || height < 0)
                throw new IllegalArgumentException("measurement is invalid");
        }
    }
}

