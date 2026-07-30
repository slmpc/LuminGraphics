package com.github.slmpc.lumingraphics.text;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

/** MIG-TEXT-RENDERER */
public interface TextRenderer extends AutoCloseable {
    TextMeasurement measure(String text, float scale, TtfFontLoader font);
    TextLayout add(String text, float x, float y, float scale, TtfFontLoader font);
    TextLayout add(String text, float x, float y, float scale, LuminColor color, TtfFontLoader font);
    TextLayout addRotated(String text, float x, float y, float scale, LuminColor color, TtfFontLoader font,
                          float originX, float originY, float rotationDegrees);
    void draw();
    void clear();
    @Override void close();
}
