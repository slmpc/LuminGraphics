package com.github.slmpc.lumingraphics.text;

/** MIG-TEXT-RENDERER */
public interface TextRenderer extends AutoCloseable {
    TextMeasurement measure(String text, float scale, TtfFontLoader font);
    TextLayout add(String text, float x, float y, float scale, TtfFontLoader font);
    void draw();
    void clear();
    @Override void close();
}
