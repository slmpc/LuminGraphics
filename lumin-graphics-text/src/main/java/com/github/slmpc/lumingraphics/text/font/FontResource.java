package com.github.slmpc.lumingraphics.text.font;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public interface FontResource {
    byte[] read() throws IOException;

    String description();

    static FontResource path(Path path) {
        Path normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        return new FontResource() {
            public byte[] read() throws IOException { return Files.readAllBytes(normalized); }
            public String description() { return normalized.toString(); }
        };
    }

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
            public String description() { return "classpath:/" + resourceName; }
        };
    }
}
