package com.github.slmpc.lumingraphics.text.render;

import com.github.slmpc.lumingraphics.text.atlas.TtfFontLoader;
import com.github.slmpc.lumingraphics.text.layout.TextLayout;
import com.github.slmpc.lumingraphics.text.layout.TextMeasurement;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

/**
 * 文本布局和批处理提交接口。
 *
 * <p>{@code add} 只累积布局和绘制批次；在目标 scheduler 层已绑定后调用 {@link #draw()}，随后使用
 * {@link #clear()} 丢弃该帧批次。字体 loader 和 renderer 都需要由调用方关闭。</p>
 */
public interface TextRenderer extends AutoCloseable {
    /**
     * 测量文本但不添加绘制批次。
     */
    TextMeasurement measure(String text, float scale, TtfFontLoader font);

    /**
     * 以默认颜色添加文本。
     */
    TextLayout add(String text, float x, float y, float scale, TtfFontLoader font);

    /**
     * 添加带颜色的文本。
     */
    TextLayout add(String text, float x, float y, float scale, LuminColor color, TtfFontLoader font);

    /**
     * 添加绕指定原点旋转的带颜色文本。
     */
    TextLayout addRotated(String text, float x, float y, float scale, LuminColor color, TtfFontLoader font,
                          float originX, float originY, float rotationDegrees);

    /**
     * 将当前批次提交到构造时配置的输出端。
     */
    void draw();

    /**
     * 清除当前帧累积的文本批次。
     */
    void clear();

    @Override
    void close();
}
