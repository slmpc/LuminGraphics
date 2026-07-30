package com.github.slmpc.lumingraphics.tooling;

import com.github.slmpc.prismrhi.shader.RhiShaderCompileOptions;
import com.github.slmpc.prismrhi.shader.RhiShaderDesc;
import com.github.slmpc.prismrhi.shader.RhiShaderOptimizationLevel;
import com.github.slmpc.prismrhi.shader.RhiShaderStage;
import com.github.slmpc.prismrhi.shaderc.ShadercCompiler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class ShaderCompilerTool {
    static final String COMPILER_ID = "com.github.slmpc.prismrhi:prism-rhi-shaderc:0.1.0";
    static final String TARGET_ID = "vulkan1.3-spirv1.6-opt-none-LUMIN_VULKAN=1";

    private ShaderCompilerTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: ShaderCompilerTool <source-dir> <resource-output-dir>");
        }
        run(Path.of(args[0]), Path.of(args[1]), () -> { });
    }

    static void run(Path sourcePath, Path outputPath,
                    ShaderGenerationTransaction.PromotionObserver promotionObserver) throws Exception {
        Path sourceDir = sourcePath.toAbsolutePath().normalize();
        Path outputRoot = outputPath.toAbsolutePath().normalize();
        if (!outputRoot.getFileName().toString().equals("shaders")
                || !outputRoot.toString().replace('\\', '/').contains("/build/generated/resources/")) {
            throw new IllegalArgumentException("refusing to replace unexpected generated directory: " + outputRoot);
        }
        ShaderGenerationTransaction.publish(outputRoot,
                () -> {
                    List<Path> sources = enumerateSources(sourceDir);
                    return new ShaderGenerationTransaction.PlannedGeneration(
                            new ShaderGenerationTransaction.CompletionSpec(sources.size(), COMPILER_ID, TARGET_ID),
                            stagingRoot -> compileAll(sourceDir, sources, stagingRoot));
                }, promotionObserver);
    }

    private static List<Path> enumerateSources(Path sourceDir) throws IOException {
        List<Path> sources;
        try (var stream = Files.walk(sourceDir)) {
            sources = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".vsh") || path.toString().endsWith(".fsh"))
                    .sorted(Comparator.comparing(path -> sourceDir.relativize(path).toString()))
                    .toList();
        }
        if (sources.size() != 37) {
            throw new IllegalStateException("expected 37 shader sources, found " + sources.size());
        }
        for (Path source : sources) {
            Files.readAllBytes(source);
        }
        return sources;
    }

    private static void compileAll(Path sourceDir, List<Path> sources, Path outputRoot) throws Exception {
        Path spirvRoot = outputRoot.resolve("assets/lumin_graphics/shaders/spirv");
        Files.createDirectories(spirvRoot);
        ShadercCompiler compiler = new ShadercCompiler();
        List<String> manifest = new ArrayList<>();
        manifest.add("source,stage,entry,compiler,target,source_sha256,spirv_sha256");
        for (Path source : sources) {
            String relative = sourceDir.relativize(source).toString().replace('\\', '/');
            RhiShaderStage stage = relative.endsWith(".vsh") ? RhiShaderStage.VERTEX : RhiShaderStage.FRAGMENT;
            byte[] retainedGlsl = Files.readAllBytes(source);
            String vulkanSource = new String(retainedGlsl, StandardCharsets.UTF_8)
                    .replaceFirst("#version 410(?: core)?", "#version 450 core");
            byte[] glsl = vulkanSource.getBytes(StandardCharsets.UTF_8);
            var desc = new RhiShaderDesc(stage, "main", relative);
            var options = RhiShaderCompileOptions.builder()
                    .sourceName(relative)
                    .optimizationLevel(RhiShaderOptimizationLevel.NONE)
                    .warningsAsErrors(false)
                    .generateDebugInfo(false)
                    .macro("LUMIN_VULKAN", "1")
                    .build();
            ByteBuffer compiled = compiler.compile(desc, ByteBuffer.wrap(glsl), options).bytes();
            byte[] spirv = new byte[compiled.remaining()];
            compiled.get(spirv);
            validateSpirv(relative, stage, spirv);
            Path output = spirvRoot.resolve(relative + ".spv");
            Files.createDirectories(output.getParent());
            Files.write(output, spirv);
            manifest.add(String.join(",", relative, stage.name(), "main",
                    COMPILER_ID, TARGET_ID, sha256(retainedGlsl), sha256(spirv)));
            System.out.println("SHADERC PASS " + relative + " " + sha256(spirv));
        }
        Files.write(outputRoot.resolve("assets/lumin_graphics/shaders/spirv-manifest.csv"), manifest,
                StandardCharsets.UTF_8);
        System.out.println("SHADERC PASS count=37 compiler=prism-rhi-shaderc:0.1.0 target=vulkan1.3/spirv1.6");
    }

    private static void validateSpirv(String source, RhiShaderStage stage, byte[] bytes) {
        if (bytes.length <= 20 || bytes.length % Integer.BYTES != 0
                || bytes[0] != 3 || bytes[1] != 2 || bytes[2] != 35 || bytes[3] != 7) {
            throw new IllegalStateException("invalid SPIR-V module: " + source);
        }
        ByteBuffer words = ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int index = 5;
        boolean validEntry = false;
        while (index < bytes.length / Integer.BYTES) {
            int instruction = words.getInt(index * Integer.BYTES);
            int wordCount = instruction >>> 16;
            if (wordCount <= 0 || index + wordCount > bytes.length / Integer.BYTES) {
                throw new IllegalStateException("invalid SPIR-V instruction: " + source);
            }
            if ((instruction & 0xffff) == 15 && wordCount >= 4) {
                int executionModel = words.getInt((index + 1) * Integer.BYTES);
                int nameOffset = (index + 3) * Integer.BYTES;
                int end = nameOffset;
                while (end < bytes.length && bytes[end] != 0) end++;
                String entry = new String(bytes, nameOffset, end - nameOffset, StandardCharsets.UTF_8);
                int expectedModel = stage == RhiShaderStage.VERTEX ? 0 : 4;
                validEntry |= entry.equals("main") && executionModel == expectedModel;
            }
            index += wordCount;
        }
        if (index != bytes.length / Integer.BYTES || !validEntry) {
            throw new IllegalStateException("invalid SPIR-V entry point: " + source);
        }
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

}
