package com.github.slmpc.lumingraphics.tooling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public final class ShaderCompilerProcessFixture {
    private ShaderCompilerProcessFixture() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException("usage: <sources> <live> <started> <entered> <release>");
        }
        Path started = Path.of(args[2]);
        Path entered = Path.of(args[3]);
        Path release = Path.of(args[4]);
        Files.writeString(started, "started");
        ShaderCompilerTool.run(Path.of(args[0]), Path.of(args[1]), () -> {
            Files.writeString(entered, "entered");
            awaitFile(release, Duration.ofSeconds(30));
        });
    }

    private static void awaitFile(Path path, Duration timeout) throws IOException {
        Instant deadline = Instant.now().plus(timeout);
        while (!Files.exists(path)) {
            if (Instant.now().isAfter(deadline)) throw new IOException("release file was not created: " + path);
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting for release file", interrupted);
            }
        }
    }
}
