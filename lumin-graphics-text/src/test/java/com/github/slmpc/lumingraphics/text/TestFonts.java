package com.github.slmpc.lumingraphics.text;

import com.github.slmpc.lumingraphics.text.font.FontResource;
import java.nio.file.Path;

final class TestFonts {
    private static final Path ROOT = Path.of(System.getProperty(
            "lumin.epsilon.root", "D:/Dev/OpenEpsilon/Epsilon-Private"))
            .resolve("common/src/main/resources/assets/epsilon/fonts");

    private TestFonts() {}

    static FontResource resource(String name) {
        return FontResource.path(ROOT.resolve(name));
    }
}
