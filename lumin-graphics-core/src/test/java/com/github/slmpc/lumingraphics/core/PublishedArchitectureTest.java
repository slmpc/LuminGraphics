package com.github.slmpc.lumingraphics.core;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishedArchitectureTest {
    @Test
    void publishedModulesHaveNoPlatformOrEpsilonDependencies() throws IOException {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.github.slmpc.lumingraphics");
        System.out.println("ARCH_PUBLISHED_CLASS_COUNT=" + classes.size());
        assertTrue(classes.size() > 4, "ArchUnit must inspect nonempty published implementation classes");
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                "net.minecraft..",
                "com.mojang.blaze3d..",
                "net.fabricmc..",
                "net.neoforged..",
                "com.github.epsilon..",
                "org.lwjgl.glfw..",
                "org.lwjgl.sdl.."
        ).check(classes);

        Path projectDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path repository = projectDirectory.getFileName().toString().equals("lumin-graphics-core")
                ? projectDirectory.getParent()
                : projectDirectory;
        for (String module : List.of(
                "lumin-graphics-core", "lumin-graphics-render", "lumin-graphics-text", "lumin-graphics-ui"
        )) {
            Path sourceRoot = repository.resolve(module).resolve("src/main/java");
            List<Path> sources;
            try (var paths = Files.walk(sourceRoot)) {
                sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
            }
            System.out.println("ARCH_MODULE_SOURCE_COUNT " + module + "=" + sources.size());
            assertTrue(!sources.isEmpty(), "published module source set must not be empty: " + module);
            for (Path source : sources) {
                String text = Files.readString(source);
                assertTrue(!containsForbiddenPackage(text), "forbidden production dependency in " + source);
            }
        }
    }

    private static boolean containsForbiddenPackage(String source) {
        return List.of(
                "net.minecraft", "com.mojang.blaze3d", "net.fabricmc", "net.neoforged",
                "com.github.epsilon", "org.lwjgl.glfw", "org.lwjgl.sdl"
        ).stream().anyMatch(source::contains);
    }
}
