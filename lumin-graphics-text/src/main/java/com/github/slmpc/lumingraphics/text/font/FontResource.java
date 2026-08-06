package com.github.slmpc.lumingraphics.text.font;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 调用方拥有的字体字节来源。
 *
 * <p>LuminGraphics artifact 不包含默认字体。通常使用 {@link #path(Path)} 指向应用随附或用户选择的
 * TTF/OTF 文件；{@link #classpath(String)} 仅查询调用方自己的 classpath 资源。</p>
 */
public interface FontResource {
    /**
     * 读取完整字体字节；调用方负责确保来源在 loader 生命周期内可读。
     */
    byte[] read() throws IOException;

    /**
     * 返回用于诊断的来源描述，不能作为稳定的资源标识。
     */
    String description();

    /**
     * 从文件系统创建字体来源。
     *
     * @param path 调用方可读取的字体文件
     * @return 延迟读取该文件的字体来源
     */
    static FontResource path(Path path) {
        Path normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        return new FontResource() {
            public byte[] read() throws IOException {
                return Files.readAllBytes(normalized);
            }

            public String description() {
                return normalized.toString();
            }
        };
    }

    /**
     * 从调用方 classpath 创建字体来源。
     *
     * @param name 不带前导斜杠的资源名；前导斜杠也会被接受
     * @return 延迟读取调用方 classpath 的字体来源
     */
    static FontResource classpath(String name) {
        String normalized = Objects.requireNonNull(name, "name").replace('\\', '/');
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        String resourceName = normalized;
        return new FontResource() {
            public byte[] read() throws IOException {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                try (InputStream input = loader.getResourceAsStream(resourceName)) {
                    if (input == null) throw new IOException("Font resource not found: " + resourceName);
                    return input.readAllBytes();
                }
            }

            public String description() {
                return "classpath:/" + resourceName;
            }
        };
    }
}
