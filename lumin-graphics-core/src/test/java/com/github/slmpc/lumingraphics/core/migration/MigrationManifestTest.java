package com.github.slmpc.lumingraphics.core.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MigrationManifestTest {
    private static final Path DEFAULT_EPSILON_ROOT = Path.of("D:/Dev/OpenEpsilon/Epsilon-Private");
    private static final Set<String> MODULES = Set.of(
            "LuminGraphics/lumin-graphics-core",
            "LuminGraphics/lumin-graphics-render",
            "LuminGraphics/lumin-graphics-text",
            "LuminGraphics/lumin-graphics-ui",
            "LuminGraphics-MC/version-common");
    private static final Set<String> DISPOSITIONS = Set.of("port", "protocol-replace", "move-to-MC");

    @Test
    void validatesMigrationManifestAgainstActualEpsilonSources() throws Exception {
        Path manifest = configuredPath("lumin.migration.manifest", "docs/migration/epsilon-surface.csv");
        assertTrue(Files.isRegularFile(manifest), "Migration manifest is missing: " + manifest);

        List<Map<String, String>> rows = CsvTable.read(manifest);
        assertEquals(53, rows.size(), "Migration manifest must contain exactly 53 data rows");
        assertRequiredFields(rows);

        Set<String> manifestSources = uniqueValues(rows, "source", "Duplicate migration source");
        assertEquals(actualSources(), manifestSources, "Migration source bijection differs from actual Epsilon Java tree");
        uniqueValues(rows, "target_public_symbol", "Duplicate migration target public symbol");

        long mcRoutes = rows.stream()
                .filter(row -> row.get("destination_repository_module").equals("LuminGraphics-MC/version-common"))
                .count();
        assertEquals(3, mcRoutes, "Exactly three Minecraft text adapters must route to LuminGraphics-MC");
        assertEquals(Set.of(
                "graphics/text/minecraft/EpsilonFontGlyph.java",
                "graphics/text/minecraft/EpsilonFontMetrics.java",
                "graphics/text/minecraft/EpsilonTextRenderable.java"),
                rows.stream()
                        .filter(row -> row.get("disposition").equals("move-to-MC"))
                        .map(row -> row.get("source"))
                        .collect(Collectors.toSet()),
                "Only the three graphics/text/minecraft sources may move to MC");
        for (Map<String, String> row : rows) {
            assertEquals(expectedModule(row.get("source")), row.get("destination_repository_module"),
                    "Source routed to the wrong module: " + row.get("source"));
        }
        assertTrue(rows.stream().noneMatch(row -> row.get("target_public_symbol").contains("com.github.epsilon")),
                "Compatibility symbols in the com.github.epsilon namespace are forbidden");
        assertEquals("com.github.slmpc.lumingraphics.text.FontRegistry",
                rows.stream().filter(row -> row.get("source").equals("graphics/text/StaticFontLoader.java"))
                        .findFirst().orElseThrow().get("target_public_symbol"));
        assertEquals("com.github.slmpc.lumingraphics.core.RenderContext",
                rows.stream().filter(row -> row.get("source").equals("graphics/LuminRenderSystem.java"))
                        .findFirst().orElseThrow().get("target_public_symbol"));

        String actualHead = gitHead(epsilonRoot());
        assertTrue(rows.stream().allMatch(row -> row.get("source_commit").equals(actualHead)),
                "Migration provenance must match actual Epsilon HEAD " + actualHead);
    }

    private static void assertRequiredFields(List<Map<String, String>> rows) {
        Set<String> fields = Set.of("source", "destination_repository_module", "target_public_symbol",
                "disposition", "preserved_behavior", "replacement_deletion_rationale",
                "behavioral_test_id", "source_commit");
        for (Map<String, String> row : rows) {
            assertTrue(row.keySet().containsAll(fields), "Migration CSV is missing required columns");
            for (String field : fields) {
                assertFalse(row.get(field).isBlank(), "Migration field must be nonempty: " + field);
                assertFalse(row.get(field).matches("(?i).*(tbd|todo|future).*"),
                        "Migration field contains a vague placeholder: " + field);
            }
            assertTrue(MODULES.contains(row.get("destination_repository_module")),
                    "Invalid destination module: " + row.get("destination_repository_module"));
            assertTrue(DISPOSITIONS.contains(row.get("disposition")),
                    "Invalid disposition: " + row.get("disposition"));
            assertTrue(row.get("target_public_symbol").matches(
                            "com\\.github\\.slmpc\\.[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)*\\.[A-Z][A-Za-z0-9]*"),
                    "Invalid target public symbol: " + row.get("target_public_symbol"));
        }
    }

    private static String expectedModule(String source) {
        if (source.startsWith("graphics/text/minecraft/")) {
            return "LuminGraphics-MC/version-common";
        }
        if (source.startsWith("graphics/text/")) {
            return "LuminGraphics/lumin-graphics-text";
        }
        if (source.startsWith("gui/lib/")) {
            return "LuminGraphics/lumin-graphics-ui";
        }
        if (source.startsWith("graphics/")) {
            return source.startsWith("graphics/buffer/")
                            || source.equals("graphics/LuminRenderSystem.java")
                            || source.equals("graphics/LuminTexture.java")
                            || source.equals("graphics/LuminVertexFormats.java")
                    ? "LuminGraphics/lumin-graphics-core"
                    : "LuminGraphics/lumin-graphics-render";
        }
        throw new IllegalArgumentException("Unexpected source route: " + source);
    }

    private static Set<String> actualSources() throws IOException {
        Path javaRoot = epsilonRoot().resolve("common/src/main/java/com/github/epsilon");
        try (var graphics = Files.walk(javaRoot.resolve("graphics"));
                var gui = Files.walk(javaRoot.resolve("gui/lib"))) {
            Set<String> sources = java.util.stream.Stream.concat(graphics, gui)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .map(path -> javaRoot.relativize(path).toString().replace('\\', '/'))
                    .collect(Collectors.toSet());
            assertEquals(53, sources.size(), "Actual Epsilon Java inventory changed");
            return sources;
        }
    }

    private static Set<String> uniqueValues(List<Map<String, String>> rows, String field, String message) {
        Set<String> values = rows.stream().map(row -> row.get(field)).collect(Collectors.toSet());
        assertEquals(rows.size(), values.size(), message);
        return values;
    }

    static Path configuredPath(String property, String relativeDefault) {
        String configured = System.getProperty(property);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path repository = Files.isRegularFile(workingDirectory.resolve("settings.gradle.kts"))
                ? workingDirectory
                : workingDirectory.getParent();
        return repository.resolve(relativeDefault).normalize();
    }

    static Path epsilonRoot() {
        return Path.of(System.getProperty("lumin.epsilon.root", DEFAULT_EPSILON_ROOT.toString()))
                .toAbsolutePath().normalize();
    }

    static String gitHead(Path repository) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "-C", repository.toString(), "rev-parse", "HEAD")
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        assertEquals(0, process.waitFor(), "Unable to read source Git HEAD: " + output);
        return output;
    }
}
