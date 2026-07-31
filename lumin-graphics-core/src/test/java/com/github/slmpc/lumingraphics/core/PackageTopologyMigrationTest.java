package com.github.slmpc.lumingraphics.core;

import org.junit.jupiter.api.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageTopologyMigrationTest {
    private static final Stage STAGE = Stage.FINAL;
    private static final String PREFIX = "com.github.slmpc.lumingraphics.";
    private static final List<String> MODULES = List.of(
            "lumin-graphics-core", "lumin-graphics-render", "lumin-graphics-text", "lumin-graphics-ui");
    private static final Pattern PROJECT_VERSION = Pattern.compile("(?m)^version = \"([^\"]+)\"$");
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;");
    private static final List<Move> MOVES = moves();
    private static final String FIRST_RETIRED_FQN = MOVES.get(0).oldFqn();

    @Test
    void baselineFinalMatrixReportsOffenders() throws IOException {
        Path repository = repository();
        if (STAGE == Stage.BASELINE) {
            AssertionError topologyError = assertThrows(AssertionError.class,
                    () -> evaluateFinalTopology(repository));
            String firstMissingNewFqn = firstMissingNewFqn(repository);
            String firstRetainedOldFqn = firstRetainedOldFqn(repository);
            assertTrue(topologyError.getMessage().contains(firstMissingNewFqn), topologyError::getMessage);
            assertTrue(topologyError.getMessage().contains(firstRetainedOldFqn), topologyError::getMessage);

            AssertionError compilerError = assertThrows(AssertionError.class,
                    () -> compileCleanConsumer(repository));
            assertTrue(compilerError.getMessage().contains(firstMissingNewFqn), compilerError::getMessage);
            System.out.println("PACKAGE_TOPOLOGY_STAGE=BASELINE");
            System.out.println("PACKAGE_TOPOLOGY_REJECTION=" + singleLine(topologyError.getMessage()));
            System.out.println("PACKAGE_CONSUMER_REJECTION=" + singleLine(compilerError.getMessage()));
        } else {
            evaluateFinalTopology(repository);
            compileCleanConsumer(repository);
            System.out.println("PACKAGE_TOPOLOGY_STAGE=FINAL");
        }
    }

    @Test
    void legacyFixtureReportsRetiredFqn() {
        Set<String> finalFqns = new LinkedHashSet<>();
        MOVES.stream().map(Move::newFqn).forEach(finalFqns::add);
        Set<String> mutatedSource = new LinkedHashSet<>(finalFqns);
        mutatedSource.add(FIRST_RETIRED_FQN);

        AssertionError error = assertThrows(AssertionError.class,
                () -> evaluateInventories(mutatedSource, finalFqns));
        assertTrue(error.getMessage().contains("retained-source:" + FIRST_RETIRED_FQN), error::getMessage);
        System.out.println("PACKAGE_TOPOLOGY_MUTATION_REJECTED=" + FIRST_RETIRED_FQN);
    }

    private static void evaluateFinalTopology(Path repository) throws IOException {
        evaluateInventories(sourceFqns(repository), jarFqns(publishedJars(repository)));
    }

    private static String firstMissingNewFqn(Path repository) throws IOException {
        Set<String> sourceFqns = sourceFqns(repository);
        return MOVES.stream()
                .map(Move::newFqn)
                .filter(fqn -> !sourceFqns.contains(fqn))
                .findFirst()
                .orElseThrow(() -> new AssertionError("baseline requires at least one missing new FQN"));
    }

    private static String firstRetainedOldFqn(Path repository) throws IOException {
        Set<String> sourceFqns = sourceFqns(repository);
        return MOVES.stream()
                .map(Move::oldFqn)
                .filter(sourceFqns::contains)
                .findFirst()
                .orElseThrow(() -> new AssertionError("baseline requires at least one retained legacy FQN"));
    }

    private static void evaluateInventories(Set<String> sourceFqns, Set<String> jarFqns) {
        List<String> offenders = new ArrayList<>();
        for (Move move : MOVES) {
            if (!sourceFqns.contains(move.newFqn())) offenders.add("missing-source:" + move.newFqn());
            if (!jarFqns.contains(move.newFqn())) offenders.add("missing-jar:" + move.newFqn());
            if (!move.oldFqn().equals(move.newFqn()) && sourceFqns.contains(move.oldFqn())) {
                offenders.add("retained-source:" + move.oldFqn());
            }
            if (!move.oldFqn().equals(move.newFqn()) && jarFqns.contains(move.oldFqn())) {
                offenders.add("retained-jar:" + move.oldFqn());
            }
        }
        if (!offenders.isEmpty()) {
            throw new AssertionError("major-package topology offenders=" + String.join(",", offenders));
        }
    }

    private static Set<String> sourceFqns(Path repository) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        for (String module : MODULES) {
            Path sourceRoot = repository.resolve(module).resolve("src/main/java");
            try (var paths = Files.walk(sourceRoot)) {
                for (Path source : paths.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                    if (source.getFileName().toString().equals("package-info.java")) continue;
                    Matcher matcher = PACKAGE_DECLARATION.matcher(Files.readString(source));
                    if (!matcher.find()) throw new AssertionError("missing-package-declaration:" + source);
                    String simpleName = source.getFileName().toString().replaceFirst("\\.java$", "");
                    result.add(matcher.group(1) + "." + simpleName);
                }
            }
        }
        return result;
    }

    private static Set<String> jarFqns(List<Path> jars) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        for (Path jar : jars) {
            try (JarFile archive = new JarFile(jar.toFile())) {
                var entries = archive.entries();
                while (entries.hasMoreElements()) {
                    String entry = entries.nextElement().getName();
                    if (entry.startsWith("com/github/slmpc/lumingraphics/")
                            && entry.endsWith(".class") && !entry.contains("$")) {
                        result.add(entry.substring(0, entry.length() - ".class".length()).replace('/', '.'));
                    }
                }
            }
        }
        return result;
    }

    private static void compileCleanConsumer(Path repository) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new AssertionError("system Java compiler is unavailable");
        List<Path> jars = publishedJars(repository);
        Path temporary = Files.createTempDirectory("lumin-package-consumer-");
        try {
            Path source = temporary.resolve("FinalPackageConsumer.java");
            Path output = Files.createDirectory(temporary.resolve("classes"));
            String imports = MOVES.stream().map(Move::newFqn).distinct()
                    .map(fqn -> "import " + fqn + ";")
                    .reduce("", (left, right) -> left + right + System.lineSeparator());
            Files.writeString(source, imports + "public final class FinalPackageConsumer {}\n", StandardCharsets.UTF_8);
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
                Iterable<? extends JavaFileObject> units = files.getJavaFileObjects(source.toFile());
                String classpath = String.join(System.getProperty("path.separator"),
                        jars.stream().map(Path::toString).toList());
                boolean compiled = compiler.getTask(null, files, diagnostics,
                        List.of("-proc:none", "-classpath", classpath, "-d", output.toString()), null, units).call();
                if (!compiled) {
                    List<String> missing = MOVES.stream().map(Move::newFqn).distinct()
                            .filter(fqn -> diagnostics.getDiagnostics().stream()
                                    .anyMatch(diagnostic -> diagnostic.getMessage(null)
                                            .contains(fqn.substring(0, fqn.lastIndexOf('.')))))
                            .toList();
                    throw new AssertionError("clean consumer compilation failed; missing-new-fqns=" + missing
                            + "; diagnostics=" + diagnostics.getDiagnostics());
                }
            }
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static List<Path> publishedJars(Path repository) throws IOException {
        Matcher matcher = PROJECT_VERSION.matcher(Files.readString(repository.resolve("build.gradle.kts")));
        if (!matcher.find()) throw new AssertionError("root project version is unavailable");
        String version = matcher.group(1);
        List<Path> jars = MODULES.stream()
                .map(module -> repository.resolve(module).resolve("build/libs/" + module + "-" + version + ".jar"))
                .toList();
        List<Path> missing = jars.stream().filter(path -> !Files.isRegularFile(path)).toList();
        if (!missing.isEmpty()) throw new AssertionError("missing published JARs=" + missing);
        return jars;
    }

    private static Path repository() {
        Path candidate = Path.of(System.getProperty("lumin.root", System.getProperty("user.dir"))).toAbsolutePath();
        while (candidate != null) {
            Path current = candidate;
            if (Files.isRegularFile(current.resolve("build.gradle.kts"))
                    && MODULES.stream().allMatch(module -> Files.isDirectory(current.resolve(module)))) {
                return current;
            }
            candidate = candidate.getParent();
        }
        throw new AssertionError("unable to locate LuminGraphics repository root");
    }

    private static String singleLine(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    private static List<Move> moves() {
        List<Move> result = new ArrayList<>();
        add(result, "core", "core.context", "LuminGraphicsContext", "RenderContext");
        add(result, "core", "core.target", "RenderTarget");
        add(result, "core", "core.threading", "RenderThreadGate");
        add(result, "render", "render.frame", "RenderExecution", "RenderFrame");
        add(result, "render", "render.resource", "RenderResources");
        add(result, "text", "text.font", "FontClosedException", "FontException", "FontLoader",
                "FontMalformedException", "FontMetrics", "FontRegistry", "FontResource", "MissingGlyphException");
        add(result, "text", "text.ttf", "TtfFontFile", "TtfGlyph");
        add(result, "text", "text.atlas", "AtlasExhaustedException", "AtlasPixelFormat", "AtlasPixels",
                "GlyphAtlasUpload", "GlyphAtlasUploader", "GlyphDescriptor", "GlyphUploadException", "GlyphUv",
                "TtfFontLoader", "TtfGlyphAtlas");
        add(result, "text", "text.layout", "GlyphPlacement", "TextLayout", "TextLayoutEngine",
                "TextMeasurement", "TextRenderBatch");
        add(result, "text", "text.render", "TextBatchSink", "TextDraw", "TextRenderer", "TtfTextRenderer");
        add(result, "text", "text.emoji", "EmojiGlyph", "SystemEmojiAtlas");
        add(result, "text", "text.icon", "IconChars");
        add(result, "ui", "ui.animation", "UiAnimation");
        add(result, "ui", "ui.control", "AssistChip", "Button", "ButtonElement", "FilledField", "IconButton",
                "Input", "InputElement", "PopupCard", "SegmentedControl", "SelectionRange", "Slider", "Switch",
                "SwitchElement");
        result.add(new Move(PREFIX + "ui.control.UiScrollBar", PREFIX + "ui.control.UiScrollBar"));
        add(result, "ui", "ui.geometry", "Insets", "UiRect");
        add(result, "ui", "ui.layout", "Axis", "LayoutScope", "LinearScope");
        add(result, "ui", "ui.node.container", "Layer", "Layered", "Scissor");
        add(result, "ui", "ui.node.primitive", "MarqueeText", "Outline", "Rect", "RectGradient", "RectOutline",
                "RotatedText", "RotatedTexture", "RoundRect", "RoundRectGradient", "SegmentedShadow", "Shadow",
                "Text", "Texture", "Triangle");
        add(result, "ui", "ui.resource", "UiResourceNotFoundException", "UiResourceResolver");
        add(result, "ui", "ui.text", "UiTextMetrics");
        add(result, "ui", "ui.theme", "UiTheme");
        add(result, "ui", "ui.tree", "UiMalformedTreeException", "UiNode", "UiNodes", "UiTree");
        add(result, "ui", "ui.viewport", "UiViewportTarget", "Viewport");
        return List.copyOf(result);
    }

    private static void add(List<Move> moves, String oldPackage, String newPackage, String... simpleNames) {
        for (String simpleName : simpleNames) {
            moves.add(new Move(PREFIX + oldPackage + "." + simpleName, PREFIX + newPackage + "." + simpleName));
        }
    }

    private enum Stage { BASELINE, FINAL }

    private record Move(String oldFqn, String newFqn) { }
}
