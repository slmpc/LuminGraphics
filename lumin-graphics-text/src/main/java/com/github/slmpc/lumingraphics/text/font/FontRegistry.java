package com.github.slmpc.lumingraphics.text.font;

import com.github.slmpc.lumingraphics.text.atlas.TtfFontLoader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 由应用定义字体 ID 到 {@link TtfFontLoader} 的注册表。
 *
 * <p>注册表拥有已登记 loader；关闭时会逐个关闭它们。UI 的字体 ID 可以通过
 * {@code UiResourceResolver} 映射到这里的 loader。</p>
 */
public final class FontRegistry implements AutoCloseable {
    private final Map<String, TtfFontLoader> fonts = new LinkedHashMap<>();
    private boolean closed;

    /**
     * 注册一个唯一字体 ID，并返回注册后的 loader。
     */
    public synchronized TtfFontLoader register(String name, Supplier<TtfFontLoader> factory) {
        ensureOpen();
        String key = normalize(name);
        if (fonts.containsKey(key)) throw new IllegalArgumentException("Font already registered: " + key);
        TtfFontLoader font = Objects.requireNonNull(factory.get(), "font factory result");
        fonts.put(key, font);
        return font;
    }

    /**
     * 返回指定字体；未知 ID 会抛出 {@link IllegalArgumentException}。
     */
    public synchronized TtfFontLoader require(String name) {
        ensureOpen();
        TtfFontLoader font = fonts.get(normalize(name));
        if (font == null) throw new IllegalArgumentException("Unknown font: " + name);
        return font;
    }

    public synchronized int size() {
        ensureOpen();
        return fonts.size();
    }

    private static String normalize(String name) {
        String key = Objects.requireNonNull(name, "name").trim();
        if (key.isEmpty()) throw new IllegalArgumentException("Font name is empty");
        return key;
    }

    private void ensureOpen() {
        if (closed) throw new FontClosedException("Font registry is closed");
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        for (TtfFontLoader font : fonts.values()) {
            try {
                font.close();
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        fonts.clear();
        if (failure != null) throw failure;
    }
}
