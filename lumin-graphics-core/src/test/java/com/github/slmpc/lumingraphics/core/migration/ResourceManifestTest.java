package com.github.slmpc.lumingraphics.core.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ResourceManifestTest {
    private static final Set<String> TYPES = Set.of("shader", "font");

    @Test
    void validatesResourceManifestAgainstActualEpsilonBytes() throws Exception {
        Path manifest = MigrationManifestTest.configuredPath(
                "lumin.resource.manifest", "docs/resources/manifest.csv");
        assertTrue(Files.isRegularFile(manifest), "Resource manifest is missing: " + manifest);

        List<Map<String, String>> rows = CsvTable.read(manifest);
        assertEquals(41, rows.size(), "Resource manifest must contain exactly 41 data rows");
        assertRequiredFields(rows);
        assertEquals(37, rows.stream().filter(row -> row.get("type").equals("shader")).count(),
                "Resource manifest must contain 37 shaders");
        assertEquals(4, rows.stream().filter(row -> row.get("type").equals("font")).count(),
                "Resource manifest must contain four fonts");

        Set<String> sources = uniqueValues(rows, "source", "Duplicate resource source");
        uniqueValues(rows, "target", "Duplicate resource target");
        assertEquals(actualResources(), sources, "Resource source bijection differs from actual Epsilon tree");

        Path resourceRoot = MigrationManifestTest.epsilonRoot()
                .resolve("common/src/main/resources/assets/epsilon");
        for (Map<String, String> row : rows) {
            Path source = resourceRoot.resolve(row.get("source"));
            assertEquals(Files.size(source), Long.parseLong(row.get("byte_size")),
                    "Resource byte size differs: " + row.get("source"));
            assertEquals(sha256(source), row.get("sha256"),
                    "Resource SHA-256 differs: " + row.get("source"));
        }

        String actualHead = MigrationManifestTest.gitHead(MigrationManifestTest.epsilonRoot());
        assertTrue(rows.stream().allMatch(row -> row.get("source_commit").equals(actualHead)),
                "Resource provenance must match actual Epsilon HEAD " + actualHead);
        Set<String> markedImports = rows.stream()
                        .filter(row -> row.get("import_rewrite_status").equals("rewrite-required-todo10"))
                        .map(row -> row.get("source"))
                        .collect(Collectors.toSet());
        assertEquals(actualMojImportShaders(), markedImports,
                "Marked import rewrites must equal shaders that actually contain #moj_import");
        assertEquals(9, markedImports.size(), "Exactly nine #moj_import shader files must be marked for rewrite");
        assertTrue(rows.stream()
                        .filter(row -> markedImports.contains(row.get("source")))
                        .allMatch(row -> row.get("target_test_id").equals("RES-MOJ-IMPORT-REWRITE")),
                "Each #moj_import shader must name its rewrite contract test");
    }

    private static void assertRequiredFields(List<Map<String, String>> rows) {
        Set<String> fields = Set.of("type", "source", "target", "sha256", "byte_size",
                "source_commit", "provenance_status", "import_rewrite_status", "target_test_id");
        for (Map<String, String> row : rows) {
            assertTrue(row.keySet().containsAll(fields), "Resource CSV is missing required columns");
            for (String field : fields) {
                assertFalse(row.get(field).isBlank(), "Resource field must be nonempty: " + field);
            }
            assertTrue(TYPES.contains(row.get("type")), "Invalid resource type: " + row.get("type"));
            assertTrue(row.get("target").startsWith("assets/lumin_graphics/"),
                    "Resource target must use assets/lumin_graphics: " + row.get("target"));
            assertFalse(row.get("target").contains("epsilon"),
                    "Resource target must not retain epsilon namespace: " + row.get("target"));
            assertTrue(row.get("sha256").matches("[0-9a-f]{64}"),
                    "Resource SHA-256 must be lowercase hexadecimal: " + row.get("source"));
            assertTrue(row.get("provenance_status").equals("source-repository; license-review-required"),
                    "Resource provenance/license status must be honest and explicit");
            assertTrue(Set.of("not-applicable", "rewrite-required-todo10")
                            .contains(row.get("import_rewrite_status")),
                    "Invalid import rewrite status: " + row.get("import_rewrite_status"));
        }
    }

    private static Set<String> actualResources() throws IOException {
        Path root = MigrationManifestTest.epsilonRoot()
                .resolve("common/src/main/resources/assets/epsilon");
        try (var shaders = Files.walk(root.resolve("shaders"));
                var fonts = Files.walk(root.resolve("fonts"))) {
            Set<String> resources = java.util.stream.Stream.concat(shaders, fonts)
                    .filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .collect(Collectors.toSet());
            assertEquals(41, resources.size(), "Actual Epsilon resource inventory changed");
            return resources;
        }
    }

    private static Set<String> actualMojImportShaders() throws IOException {
        Path root = MigrationManifestTest.epsilonRoot()
                .resolve("common/src/main/resources/assets/epsilon");
        try (var shaders = Files.walk(root.resolve("shaders"))) {
            return shaders.filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            return Files.readString(path).lines().anyMatch(line -> line.startsWith("#moj_import"));
                        } catch (IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    })
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .collect(Collectors.toSet());
        }
    }

    private static Set<String> uniqueValues(List<Map<String, String>> rows, String field, String message) {
        Set<String> values = rows.stream().map(row -> row.get(field)).collect(Collectors.toSet());
        assertEquals(rows.size(), values.size(), message);
        return values;
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
