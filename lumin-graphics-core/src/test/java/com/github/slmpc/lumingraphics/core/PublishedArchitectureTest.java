package com.github.slmpc.lumingraphics.core;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishedArchitectureTest {
    @Test
    void mutationFixturesReportExactImportAndLedgerOffenders() {
        String importPath = "fixture/MinecraftLeak.java";
        AssertionError importError = assertThrows(AssertionError.class, () -> rejectSource(importPath,
                "import net.minecraft.client.Minecraft; class MinecraftLeak {}"));
        assertTrue(importError.getMessage().contains(importPath), importError::getMessage);

        String ledgerPath = "docs/resources/manifest.csv";
        AssertionError ledgerError = assertThrows(AssertionError.class,
                () -> rejectLedgerCounts(ledgerPath, 36, 4));
        assertTrue(ledgerError.getMessage().contains(ledgerPath), ledgerError::getMessage);
        System.out.println("ARCH_LUMIN_MUTATIONS rejected=net.minecraft.import,ledger-count-drift offenders=2");
    }

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

        Path repository = Path.of(System.getProperty("lumin.root", System.getProperty("user.dir"))).toAbsolutePath();
        int sourceCount = 0;
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
            sourceCount += sources.size();
            for (Path source : sources) {
                String text = Files.readString(source);
                rejectSource(source.toString(), text);
            }
        }
        int jarEntries = 0;
        int classEntries = 0;
        Set<String> namespaces = new HashSet<>();
        for (String module : List.of("lumin-graphics-core", "lumin-graphics-render", "lumin-graphics-text", "lumin-graphics-ui")) {
            Path jar = repository.resolve(module).resolve("build/libs/" + module + "-0.1.0.jar");
            assertTrue(Files.isRegularFile(jar), "missing published JAR: " + jar);
            try (JarFile archive = new JarFile(jar.toFile())) {
                var entries = archive.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    jarEntries++;
                    if (entry.getName().endsWith(".class")) {
                        classEntries++;
                        String prefix = "com/github/slmpc/lumingraphics/";
                        assertTrue(entry.getName().startsWith(prefix), "unapproved public namespace in " + jar + "!" + entry.getName());
                        String tail = entry.getName().substring(prefix.length());
                        namespaces.add(tail.substring(0, tail.indexOf('/')));
                    }
                    assertTrue(!entry.getName().startsWith("META-INF/services/"), "service descriptor in " + jar + "!" + entry.getName());
                }
            }
        }
        assertTrue(sourceCount > 20 && classEntries > 20 && jarEntries > classEntries,
                "architecture source/class/JAR counts must be nonzero");
        assertTrue(namespaces.equals(Set.of("core", "render", "text", "ui")), "public namespace ledger drift: " + namespaces);
        List<String> migration = Files.readAllLines(repository.resolve("docs/migration/epsilon-surface.csv"));
        List<String> resources = Files.readAllLines(repository.resolve("docs/resources/manifest.csv"));
        assertTrue(migration.size() == 54, "epsilon ledger count drift: " + (migration.size() - 1));
        long shaders = resources.stream().skip(1).filter(row -> row.startsWith("shader,")).count();
        long fonts = resources.stream().skip(1).filter(row -> row.startsWith("font,")).count();
        rejectLedgerCounts(repository.resolve("docs/resources/manifest.csv").toString(), shaders, fonts);
        System.out.printf("ARCH_LUMIN_COUNTS sources=%d classes=%d jars=4 entries=%d migration=53 shaders=37 fonts=4 namespaces=4%n",
                sourceCount, classEntries, jarEntries);
    }

    private static boolean containsForbiddenPackage(String source) {
        return List.of(
                "net.minecraft", "com.mojang.blaze3d", "net.fabricmc", "net.neoforged",
                "com.github.epsilon", "org.lwjgl.glfw", "org.lwjgl.sdl"
        ).stream().anyMatch(source::contains);
    }

    private static void rejectSource(String path, String source) {
        if (containsForbiddenPackage(source)) throw new AssertionError("forbidden production dependency in " + path);
    }

    private static void rejectLedgerCounts(String path, long shaders, long fonts) {
        if (shaders != 37 || fonts != 4) {
            throw new AssertionError("resource ledger count drift in " + path + ": shaders=" + shaders + " fonts=" + fonts);
        }
    }
}
