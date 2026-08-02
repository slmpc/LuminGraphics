package com.github.slmpc.lumingraphics.render;

import com.github.slmpc.lumingraphics.render.pipeline.LuminPipelineCatalog;
import com.github.slmpc.lumingraphics.render.shader.ShaderArtifactLibrary;
import com.github.slmpc.prismrhi.shader.RhiShaderBinaryFormat;
import com.github.slmpc.prismrhi.command.RhiPrimitiveTopology;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShaderArtifactTest {
    private static final Path ROOT = locateRoot();
    private static final Path RESOURCES = ROOT.resolve("lumin-graphics-render/src/main/resources");
    private static final Path GENERATED = ROOT.resolve("lumin-graphics-render/build/generated/resources/shaders");

    private static Path locateRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }

    @Test
    void mapsEveryTodo8ShaderToNeutralSourceSpirvAndCatalog() throws Exception {
        List<String[]> rows = Files.readAllLines(ROOT.resolve("docs/resources/manifest.csv"), StandardCharsets.UTF_8)
                .stream().skip(1).map(line -> line.split(",", -1))
                .filter(row -> row[0].equals("shader")).toList();
        assertEquals(37, rows.size(), "Todo 8 shader count changed");
        Path generatedSpirv = GENERATED.resolve("assets/lumin_graphics/shaders/spirv");
        try (Stream<Path> artifacts = Files.walk(generatedSpirv)) {
            assertEquals(37, artifacts.filter(path -> path.toString().endsWith(".spv")).count(),
                    "generated SPIR-V tree contains stale or missing artifacts");
        }
        Path generatedManifestPath = GENERATED.resolve("assets/lumin_graphics/shaders/spirv-manifest.csv");
        List<String> generatedManifest = Files.readAllLines(generatedManifestPath);
        assertEquals(38, generatedManifest.size(), "generated manifest must contain 37 shader rows");
        Map<String, String> completion = Files.readAllLines(GENERATED.resolve("generation-complete.properties"))
                .stream().map(line -> line.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(columns -> columns[0], columns -> columns[1]));
        assertEquals("1", completion.get("format"));
        assertEquals("37", completion.get("source_count"));
        assertEquals("com.github.slmpc.prismrhi:prism-rhi-shaderc:0.1.0", completion.get("compiler"));
        assertEquals("vulkan1.3-spirv1.6-opt-none-LUMIN_VULKAN=1", completion.get("target"));
        assertEquals(sha256(Files.readAllBytes(generatedManifestPath)), completion.get("manifest_sha256"),
                "completion marker does not bind the generated manifest");
        try (Stream<Path> siblings = Files.list(GENERATED.getParent())) {
            assertTrue(siblings.map(path -> path.getFileName().toString())
                    .noneMatch(name -> name.equals("shaders.backup") || name.startsWith("shaders.staging-")),
                    "generated shader transaction directory remains");
        }
        Map<String, String> generatedSourceHashes = generatedManifest.stream().skip(1)
                .map(line -> line.split(",", -1))
                .collect(java.util.stream.Collectors.toMap(columns -> columns[0], columns -> columns[5]));

        List<LuminPipelineCatalog.PipelineDescriptor> entries = LuminPipelineCatalog.entries();
        Set<String> covered = new HashSet<>();
        entries.forEach(entry -> {
            covered.add(entry.vertex().sourcePath());
            covered.add(entry.fragment().sourcePath());
        });

        for (String[] row : rows) {
            String relative = row[2].substring("assets/lumin_graphics/shaders/".length());
            Path source = RESOURCES.resolve(row[2]);
            assertTrue(Files.isRegularFile(source), "missing shader source: " + row[2]);
            String glsl = Files.readString(source);
            validateSource(relative, glsl, row[7].equals("rewrite-required-todo10"));
            validateFreshness(Files.readAllBytes(source), generatedSourceHashes.get(relative));
            String spirvRelative = relative.replaceAll("\\.(vsh|fsh)$", ".$1.spv");
            Path spirv = GENERATED.resolve("assets/lumin_graphics/shaders/spirv").resolve(spirvRelative);
            assertTrue(Files.isRegularFile(spirv), "missing SPIR-V: " + spirvRelative);
            byte[] bytes = Files.readAllBytes(spirv);
            validateSpirv(spirvRelative, bytes);
        }
        validateCoverage(rows.stream().map(row -> row[2].substring("assets/lumin_graphics/shaders/".length())).collect(java.util.stream.Collectors.toSet()), covered);
        for (var entry : entries) {
            String vertex = Files.readString(RESOURCES.resolve("assets/lumin_graphics/shaders").resolve(entry.vertex().sourcePath()));
            entry.vertexLayout().locations().forEach(location -> assertTrue(
                    vertex.contains("layout(location = " + location + ") in"),
                    entry.id() + " vertex schema location missing: " + location));
            String fragment = Files.readString(RESOURCES.resolve("assets/lumin_graphics/shaders").resolve(entry.fragment().sourcePath()));
            entry.samplers().forEach(sampler -> assertTrue(fragment.matches("(?s).*uniform\\s+sampler\\w*\\s+" + sampler + "\\s*;.*"),
                    entry.id() + " declares missing sampler " + sampler));
        }
    }

    @Test
    void rejectsNamedSourceAbiMutations() throws Exception {
        String vertex = Files.readString(RESOURCES.resolve("assets/lumin_graphics/shaders/rectangle.vsh"));
        assertFailure(() -> validateSource("rectangle.vsh", vertex.replace("LuminFrame", "BrokenFrame"), true), "LuminFrame ABI missing");
        assertFailure(() -> validateSource("rectangle.vsh", vertex.replace("Projection", "BrokenProjection"), true), "Projection uniform missing");
        assertFailure(() -> validateSource("rectangle.vsh", vertex.replace("layout(location = 0) in", "in"), true), "position location missing");
        assertFailure(() -> validateSource("rectangle.vsh", "#moj_import <legacy>\n" + vertex, true), "Minecraft import remains");
        String collidingDrawBlock = vertex.replace("layout(location = 0) in",
                "layout(std140) uniform LuminDraw { mat4 Transform; };\nlayout(location = 0) in");
        assertFailure(() -> validateSource("rectangle.vsh", collidingDrawBlock, true), "colliding LuminDraw block");

        String texture = Files.readString(RESOURCES.resolve("assets/lumin_graphics/shaders/texture.fsh"));
        assertTrue(texture.contains("LUMIN_BINDING(1) uniform sampler2D Sampler0"));
        assertTrue(Files.readString(RESOURCES.resolve("assets/lumin_graphics/shaders/ttf_font_aa.fsh"))
                .contains("LUMIN_BINDING(1) uniform sampler2D Sampler0"));
        assertTrue(Files.readString(RESOURCES.resolve("assets/lumin_graphics/shaders/ttf_font_no_aa.fsh"))
                .contains("LUMIN_BINDING(1) uniform sampler2D Sampler0"));
        assertFailure(() -> validateSampler(texture.replace("Sampler0", "BrokenSampler"), "Sampler0"), "sampler missing");
    }

    @Test
    void ttfAaShaderReconstructsTheStbSignedDistanceEdge() throws Exception {
        String shader = Files.readString(
                RESOURCES.resolve("assets/lumin_graphics/shaders/ttf_font_aa.fsh"));

        assertTrue(shader.contains("EDGE_THRESHOLD = 0.5"));
        assertTrue(shader.contains("fwidth(signedDistance)"));
        assertTrue(shader.contains("smoothstep(-edgeWidth, edgeWidth, signedDistance)"));
    }

    @Test
    void rejectsNamedSpirvMutations() throws Exception {
        Path path = GENERATED.resolve("assets/lumin_graphics/shaders/spirv/rectangle.vsh.spv");
        byte[] original = Files.readAllBytes(path);
        byte[] magic = original.clone();
        magic[0] = 0;
        assertFailure(() -> validateSpirv("magic", magic), "invalid SPIR-V magic");
        byte[] instruction = original.clone();
        instruction[22] = 0;
        instruction[23] = 0;
        assertFailure(() -> validateSpirv("instruction", instruction), "invalid SPIR-V instruction");
        byte[] entry = original.clone();
        int main = indexOf(entry, new byte[]{'m', 'a', 'i', 'n', 0});
        assertTrue(main > 0, "fixture main entry not found");
        entry[main] = 'x';
        assertFailure(() -> validateSpirv("entry", entry), "SPIR-V main entry missing");
        byte[] stage = original.clone();
        ByteBuffer stageWords = ByteBuffer.wrap(stage).order(ByteOrder.LITTLE_ENDIAN);
        int entryInstruction = findInstruction(stageWords, stage.length / 4, 15);
        assertTrue(entryInstruction >= 5, "fixture entry instruction not found");
        stageWords.putInt((entryInstruction + 1) * 4, 4);
        assertFailure(() -> validateSpirv("rectangle.vsh.spv", stage), "SPIR-V stage mismatch");
    }

    @Test
    void rejectsCatalogOrphanMutation() {
        Set<String> expected = Set.of("rectangle.vsh", "rectangle.fsh");
        assertFailure(() -> validateCoverage(expected, Set.of("rectangle.vsh")), "catalog orphan: rectangle.fsh");
    }

    @Test
    void runtimeLibraryReturnsActualCatalogSourceAndSpirvBytes() throws Exception {
        var shader = LuminPipelineCatalog.require("rectangle").vertex();
        byte[] source = remaining(ShaderArtifactLibrary.load(shader, RhiShaderBinaryFormat.OPENGL_SOURCE));
        byte[] spirv = remaining(ShaderArtifactLibrary.load(shader, RhiShaderBinaryFormat.SPIRV));
        assertTrue(Arrays.equals(source, Files.readAllBytes(RESOURCES.resolve("assets/lumin_graphics/shaders/rectangle.vsh"))));
        validateSpirv(shader.spirvPath(), spirv);
    }

    @Test
    void catalogUsesOnlyPrismSupportedTopologies() {
        Set<String> supported = Arrays.stream(RhiPrimitiveTopology.values()).map(Enum::name)
                .collect(java.util.stream.Collectors.toSet());
        LuminPipelineCatalog.entries().forEach(entry -> assertTrue(supported.contains(entry.topology().name()),
                entry.id() + " uses topology unsupported by Prism: " + entry.topology()));
        LuminPipelineCatalog.entries().forEach(entry -> {
            assertTrue(entry.topology() == RhiPrimitiveTopology.TRIANGLE_LIST,
                    entry.id() + " must use triangle-list submission");
            assertTrue(entry.drawAbi().cpuTransformedPositions(), entry.id() + " needs CPU-transformed positions");
        });
    }

    @Test
    void targetShaderTreeContainsNoLegacyBrandTokens() throws Exception {
        Path tree = RESOURCES.resolve("assets/lumin_graphics/shaders");
        try (Stream<Path> paths = Files.walk(tree)) {
            for (Path path : paths.toList()) {
                String relative = tree.relativize(path).toString().replace('\\', '/');
                assertTrue(!relative.toLowerCase().contains("minecraft"), "legacy target path: " + relative);
                if (Files.isRegularFile(path)) {
                    String source = Files.readString(path);
                    assertTrue(!source.matches("(?s).*((?i:\\bminecraft\\b|\\bmojang\\b)|\\bEpsilon\\b).*"),
                            "legacy target token: " + relative);
                }
            }
        }
        assertTrue(!LuminPipelineCatalog.entries().toString().toLowerCase().contains("minecraft"),
                "legacy token remains in pipeline catalog");
    }

    @Test
    void rejectsStaleGenerationMutation() throws Exception {
        byte[] source = Files.readAllBytes(RESOURCES.resolve("assets/lumin_graphics/shaders/rectangle.vsh"));
        String expected = sha256(source);
        byte[] changed = Arrays.copyOf(source, source.length + 1);
        assertFailure(() -> validateFreshness(changed, expected), "stale generated source hash");
    }

    private static void validateSource(String relative, String glsl, boolean rewrittenVertex) {
        require(!glsl.contains("#moj_import"), "Minecraft import remains: " + relative);
        require(!glsl.contains("minecraft:"), "Minecraft namespace remains: " + relative);
        require(glsl.contains("void main"), "main entry point missing: " + relative);
        if (rewrittenVertex) {
            require(glsl.contains("uniform LuminFrame"), "LuminFrame ABI missing: " + relative);
            require(glsl.contains("mat4 Projection;"), "Projection uniform missing: " + relative);
            require(glsl.contains("vec4 Viewport;"), "Viewport uniform missing: " + relative);
            require(!glsl.contains("LuminDraw"), "colliding LuminDraw block: " + relative);
            require(!glsl.contains("mat4 Transform;"), "per-draw transform must be CPU-applied: " + relative);
            require(glsl.contains("layout(location = 0) in"), "position location missing: " + relative);
        }
    }

    private static void validateSampler(String source, String sampler) {
        require(source.matches("(?s).*uniform\\s+sampler\\w*\\s+" + sampler + "\\s*;.*"), "sampler missing: " + sampler);
    }

    private static void validateCoverage(Set<String> expected, Set<String> covered) {
        expected.stream().filter(path -> !covered.contains(path)).findFirst()
                .ifPresent(path -> { throw new IllegalArgumentException("catalog orphan: " + path); });
    }

    private static void validateSpirv(String name, byte[] bytes) {
        require(bytes.length > 20 && bytes.length % 4 == 0, "truncated SPIR-V: " + name);
        require(Arrays.equals(Arrays.copyOf(bytes, 4), new byte[]{3, 2, 35, 7}), "invalid SPIR-V magic: " + name);
        ByteBuffer words = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int index = 5;
        boolean mainEntry = false;
        boolean stageMatches = !name.contains(".vsh") && !name.contains(".fsh");
        while (index < bytes.length / 4) {
            int instruction = words.getInt(index * 4);
            int wordCount = instruction >>> 16;
            int opcode = instruction & 0xffff;
            require(wordCount > 0 && index + wordCount <= bytes.length / 4, "invalid SPIR-V instruction: " + name);
            if (opcode == 15 && wordCount >= 4) {
                int executionModel = words.getInt((index + 1) * 4);
                int nameOffset = (index + 3) * 4;
                int end = nameOffset;
                while (end < bytes.length && bytes[end] != 0) end++;
                boolean main = new String(bytes, nameOffset, end - nameOffset, StandardCharsets.UTF_8).equals("main");
                mainEntry |= main;
                if (main) {
                    stageMatches = name.contains(".vsh") ? executionModel == 0
                            : name.contains(".fsh") ? executionModel == 4 : true;
                }
            }
            index += wordCount;
        }
        require(index == bytes.length / 4, "invalid SPIR-V instruction: " + name);
        require(mainEntry, "SPIR-V main entry missing: " + name);
        require(stageMatches, "SPIR-V stage mismatch: " + name);
    }

    private static int findInstruction(ByteBuffer words, int wordLength, int wantedOpcode) {
        int index = 5;
        while (index < wordLength) {
            int instruction = words.getInt(index * 4);
            int wordCount = instruction >>> 16;
            if ((instruction & 0xffff) == wantedOpcode) return index;
            if (wordCount <= 0) return -1;
            index += wordCount;
        }
        return -1;
    }

    private static void validateFreshness(byte[] source, String generatedHash) {
        require(generatedHash != null && generatedHash.equals(sha256(source)), "stale generated source hash");
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static int indexOf(byte[] bytes, byte[] needle) {
        outer: for (int i = 0; i <= bytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (bytes[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static byte[] remaining(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static void assertFailure(org.junit.jupiter.api.function.Executable executable, String message) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, executable);
        assertTrue(error.getMessage().contains(message), error.getMessage());
    }
}
