package com.github.slmpc.lumingraphics.tooling;

import com.github.slmpc.prismrhi.RhiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderCompilerTransactionTest {
    private static final Path ROOT = locateRoot();
    private static final Path REAL_SOURCES = ROOT.resolve(
            "lumin-graphics-render/src/main/resources/assets/lumin_graphics/shaders");
    private static final Path GENERATED = ROOT.resolve("lumin-graphics-render/build/generated/resources/shaders");
    private static final String COMPLETE_MARKER = "generation-complete.properties";

    @TempDir
    Path temporaryDirectory;

    @Test
    void malformedSourceCountPreservesLastCompletedTree() throws Exception {
        Path sources = copySources("count-sources");
        Files.delete(sources.resolve("triangle.fsh"));
        Path live = seedLiveTree();
        String before = treeHash(live);

        assertThrows(IllegalStateException.class, () -> run(sources, live));

        assertEquals(before, treeHash(live), "malformed source count replaced the last completed tree");
        assertNoTransactionDirectories(live);
    }

    @Test
    void compilerFailureMidSetPreservesLastCompletedTree() throws Exception {
        Path sources = copySources("syntax-sources");
        Files.writeString(sources.resolve("ttf_font_no_aa.fsh"), "#version 410 core\nthis is invalid glsl\n");
        Path live = seedLiveTree();
        String before = treeHash(live);

        assertThrows(RhiException.class, () -> run(sources, live));

        assertEquals(before, treeHash(live), "compiler failure exposed a partial generated tree");
        assertNoTransactionDirectories(live);
    }

    @Test
    void startupCleansIncompleteStagingWithoutTouchingLive() throws Exception {
        Path sources = copySources("staging-sources");
        Files.delete(sources.resolve("triangle.fsh"));
        Path live = seedLiveTree();
        Path abandoned = live.resolveSibling("shaders.staging-abandoned");
        Files.createDirectories(abandoned);
        Files.writeString(abandoned.resolve("partial.spv"), "partial");
        String before = treeHash(live);

        assertThrows(IllegalStateException.class, () -> run(sources, live));

        assertEquals(before, treeHash(live));
        assertFalse(Files.exists(abandoned), "abandoned staging tree was not cleaned");
    }

    @Test
    void startupRestoresBackupWhenLiveIsAbsent() throws Exception {
        Path sources = copySources("backup-sources");
        Files.delete(sources.resolve("triangle.fsh"));
        Path live = outputRoot();
        Path backup = live.resolveSibling("shaders.backup");
        copyTree(GENERATED, backup);
        String before = treeHash(backup);

        assertThrows(IllegalStateException.class, () -> run(sources, live));

        assertTrue(Files.isDirectory(live), "completed backup was not restored");
        assertEquals(before, treeHash(live));
        assertFalse(Files.exists(backup));
    }

    @Test
    void startupRejectsBackupWithoutValidCompletionMarker() throws Exception {
        Path live = outputRoot();
        Path backup = live.resolveSibling("shaders.backup");
        copyTree(GENERATED, backup);
        Files.delete(backup.resolve(COMPLETE_MARKER));

        IOException failure = assertThrows(IOException.class,
                () -> ShaderGenerationTransaction.recover(live));

        assertTrue(failure.getMessage().contains("no completed live or backup tree"));
        assertFalse(Files.exists(live), "incomplete backup was accepted as live output");
        assertTrue(Files.isDirectory(backup), "rejected backup should remain available for diagnosis");
    }

    @Test
    void promotionFailureAfterBackupMoveRollsBackLastCompletedTree() throws Exception {
        Path live = seedLiveTree();
        String before = treeHash(live);

        assertThrows(IOException.class, () -> ShaderCompilerTool.run(REAL_SOURCES, live,
                () -> { throw new IOException("simulated promotion interruption"); }));

        assertEquals(before, treeHash(live), "promotion failure did not restore the last completed tree");
        assertNoTransactionDirectories(live);
    }

    @Test
    void successfulGenerationRemovesStaleModuleAndPublishesCompletionMarker() throws Exception {
        Path live = seedLiveTree();
        Files.writeString(live.resolve("assets/lumin_graphics/shaders/spirv/stale-38.fsh.spv"), "stale");

        run(REAL_SOURCES, live);

        Path spirv = live.resolve("assets/lumin_graphics/shaders/spirv");
        try (var files = Files.walk(spirv)) {
            assertEquals(37, files.filter(path -> path.toString().endsWith(".spv")).count());
        }
        assertTrue(Files.isRegularFile(live.resolve(COMPLETE_MARKER)), "completion marker missing");
        assertFalse(Files.exists(spirv.resolve("stale-38.fsh.spv")));
        assertNoTransactionDirectories(live);
    }

    @Test
    void sameRootConcurrentGenerationsBothSucceed() throws Exception {
        Path live = seedLiveTree();
        String expected = treeHash(live);
        CountDownLatch firstAtBackup = new CountDownLatch(1);
        CountDownLatch secondAtBackup = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                run(REAL_SOURCES, live, () -> {
                    firstAtBackup.countDown();
                    await(releaseFirst, "first invocation was not released");
                });
                return null;
            });
            await(firstAtBackup, "first invocation never reached backup promotion");
            var second = executor.submit(() -> {
                run(REAL_SOURCES, live.resolve("..").resolve("shaders"), secondAtBackup::countDown);
                return null;
            });

            assertFalse(secondAtBackup.await(2, TimeUnit.SECONDS),
                    "second same-root invocation entered promotion while the first owned the transaction");

            releaseFirst.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "concurrent shader workers did not stop");
        }

        assertValidGeneratedTree(live, expected);
        assertNoTransactionDirectories(live);
        assertTrue(Files.isRegularFile(live.resolveSibling(".shaders.generation.lock")),
                "stable process lock file must survive transaction completion");
    }

    @Test
    void separateProcessesSerializeSameRootAndPublishDeterministicTree() throws Exception {
        Path live = seedLiveTree();
        String expected = treeHash(live);
        Path phases = temporaryDirectory.resolve("process-phases");
        Files.createDirectories(phases);
        Process first = startProcess(live, phases, "first");
        Process second = null;
        try {
            awaitFile(phases.resolve("first.entered"), Duration.ofSeconds(30));
            second = startProcess(live, phases, "second");
            awaitFile(phases.resolve("second.started"), Duration.ofSeconds(30));
            assertFalse(awaitFileIfPresent(phases.resolve("second.entered"), Duration.ofSeconds(2)),
                    "second process entered promotion while the first process owned the lock");

            Files.writeString(phases.resolve("first.release"), "release");
            assertProcessSuccess(first, phases.resolve("first.log"));
            awaitFile(phases.resolve("second.entered"), Duration.ofSeconds(30));
            Files.writeString(phases.resolve("second.release"), "release");
            assertProcessSuccess(second, phases.resolve("second.log"));
        } finally {
            Files.writeString(phases.resolve("first.release"), "release");
            Files.writeString(phases.resolve("second.release"), "release");
            stopProcess(first);
            if (second != null) stopProcess(second);
        }

        assertValidGeneratedTree(live, expected);
        assertNoTransactionDirectories(live);
    }

    @Test
    void failedConcurrentGenerationCannotCorruptSuccessfulPeer() throws Exception {
        Path live = seedLiveTree();
        String expected = treeHash(live);
        Path invalidSources = copySources("concurrent-invalid-sources");
        Files.writeString(invalidSources.resolve("ttf_font_no_aa.fsh"),
                "#version 410 core\nthis is invalid glsl\n");
        CountDownLatch successfulAtBackup = new CountDownLatch(1);
        CountDownLatch failedStarted = new CountDownLatch(1);
        CountDownLatch releaseSuccessful = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var successful = executor.submit(() -> {
                run(REAL_SOURCES, live, () -> {
                    successfulAtBackup.countDown();
                    await(releaseSuccessful, "successful invocation was not released");
                });
                return null;
            });
            await(successfulAtBackup, "successful invocation never reached promotion");
            var failed = executor.submit(() -> {
                failedStarted.countDown();
                run(invalidSources, live);
                return null;
            });
            await(failedStarted, "failing invocation did not start");
            assertFalse(failed.isDone(), "failing invocation bypassed the successful owner's transaction lock");

            releaseSuccessful.countDown();
            successful.get(30, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> failed.get(30, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof RhiException,
                    "expected real shader compiler failure, got " + failure.getCause());
        } finally {
            releaseSuccessful.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "concurrent shader workers did not stop");
        }

        assertValidGeneratedTree(live, expected);
        assertNoTransactionDirectories(live);
    }

    @Test
    void processFailureReleasesLockAndRepeatedRecoverySucceeds() throws Exception {
        Path live = seedLiveTree();
        String expected = treeHash(live);
        for (int attempt = 0; attempt < 2; attempt++) {
            Path phases = temporaryDirectory.resolve("interruption-" + attempt);
            Files.createDirectories(phases);
            Process interrupted = startProcess(live, phases, "interrupted");
            Process recovery = null;
            try {
                awaitFile(phases.resolve("interrupted.entered"), Duration.ofSeconds(30));
                interrupted.destroyForcibly();
                assertTrue(interrupted.waitFor(10, TimeUnit.SECONDS), "interrupted shader process did not stop");

                recovery = startProcess(live, phases, "recovery");
                awaitFile(phases.resolve("recovery.entered"), Duration.ofSeconds(30));
                Files.writeString(phases.resolve("recovery.release"), "release");
                assertProcessSuccess(recovery, phases.resolve("recovery.log"));
            } finally {
                Files.writeString(phases.resolve("interrupted.release"), "release");
                Files.writeString(phases.resolve("recovery.release"), "release");
                stopProcess(interrupted);
                if (recovery != null) stopProcess(recovery);
            }
            assertValidGeneratedTree(live, expected);
            assertNoTransactionDirectories(live);
        }
    }

    @Test
    void differentOutputRootsDoNotBlockEachOther() throws Exception {
        Path firstLive = seedLiveTree("first-root");
        Path secondLive = seedLiveTree("second-root");
        String expected = treeHash(firstLive);
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                run(REAL_SOURCES, firstLive, () -> {
                    bothEntered.countDown();
                    await(release, "different-root invocation was not released");
                });
                return null;
            });
            var second = executor.submit(() -> {
                run(REAL_SOURCES, secondLive, () -> {
                    bothEntered.countDown();
                    await(release, "different-root invocation was not released");
                });
                return null;
            });
            await(bothEntered, "different output roots blocked each other");
            release.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "different-root workers did not stop");
        }

        assertValidGeneratedTree(firstLive, expected);
        assertValidGeneratedTree(secondLive, expected);
        assertNoTransactionDirectories(firstLive);
        assertNoTransactionDirectories(secondLive);
    }

    private Path copySources(String name) throws IOException {
        Path copy = temporaryDirectory.resolve(name);
        copyTree(REAL_SOURCES, copy);
        return copy;
    }

    private Path seedLiveTree() throws IOException {
        Path live = outputRoot();
        copyTree(GENERATED, live);
        return live;
    }

    private Path seedLiveTree(String name) throws IOException {
        Path live = temporaryDirectory.resolve(name).resolve("build/generated/resources/shaders");
        copyTree(GENERATED, live);
        return live;
    }

    private Path outputRoot() {
        return temporaryDirectory.resolve("build/generated/resources/shaders");
    }

    private static void run(Path sources, Path live) throws Exception {
        ShaderCompilerTool.main(new String[]{sources.toString(), live.toString()});
    }

    private static void run(Path sources, Path live,
                            ShaderGenerationTransaction.PromotionObserver observer) throws Exception {
        ShaderCompilerTool.run(sources, live, observer);
    }

    private static void await(CountDownLatch latch, String failure) throws IOException {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) throw new IOException(failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for concurrent shader generation", interrupted);
        }
    }

    private Process startProcess(Path live, Path phases, String name) throws IOException {
        String classpath = System.getProperty("lumin.shader.testClasspath");
        if (classpath == null || classpath.isBlank()) throw new IOException("shader process test classpath missing");
        String javaBinary = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        return new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", javaBinary).toString(),
                "-cp", classpath,
                ShaderCompilerProcessFixture.class.getName(),
                REAL_SOURCES.toString(), live.toString(),
                phases.resolve(name + ".started").toString(),
                phases.resolve(name + ".entered").toString(),
                phases.resolve(name + ".release").toString())
                .redirectErrorStream(true)
                .redirectOutput(phases.resolve(name + ".log").toFile())
                .start();
    }

    private static void assertProcessSuccess(Process process, Path log) throws Exception {
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "shader compiler child process did not exit");
        assertEquals(0, process.exitValue(), () -> "shader compiler child failed:\n" + readString(log));
    }

    private static void stopProcess(Process process) throws InterruptedException {
        if (!process.isAlive()) return;
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS), "shader compiler child could not be stopped");
        }
    }

    private static void awaitFile(Path path, Duration timeout) throws Exception {
        assertTrue(awaitFileIfPresent(path, timeout), "phase file was not created: " + path);
    }

    private static boolean awaitFileIfPresent(Path path, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (!Files.exists(path) && Instant.now().isBefore(deadline)) Thread.sleep(10);
        return Files.exists(path);
    }

    private static String readString(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path) : "<missing log>";
        } catch (IOException failure) {
            return "<unreadable log: " + failure + ">";
        }
    }

    private static void assertValidGeneratedTree(Path live, String expectedHash) throws Exception {
        assertEquals(expectedHash, treeHash(live), "concurrent generation changed deterministic output");
        Path manifest = live.resolve("assets/lumin_graphics/shaders/spirv-manifest.csv");
        List<String> rows = Files.readAllLines(manifest);
        assertEquals(38, rows.size(), "generated manifest must contain 37 shader rows");
        try (var modules = Files.walk(live.resolve("assets/lumin_graphics/shaders/spirv"))) {
            assertEquals(37, modules.filter(path -> path.toString().endsWith(".spv")).count());
        }
        Map<String, String> marker = Files.readAllLines(live.resolve(COMPLETE_MARKER)).stream()
                .map(line -> line.split("=", 2))
                .collect(Collectors.toMap(columns -> columns[0], columns -> columns[1]));
        assertEquals("37", marker.get("source_count"));
        assertEquals(sha256(Files.readAllBytes(manifest)), marker.get("manifest_sha256"));
    }

    private static void assertNoTransactionDirectories(Path live) throws IOException {
        Path parent = live.getParent();
        try (var children = Files.list(parent)) {
            List<String> leftovers = children.map(path -> path.getFileName().toString())
                    .filter(name -> name.equals("shaders.backup") || name.startsWith("shaders.staging-"))
                    .toList();
            assertTrue(leftovers.isEmpty(), "transaction directories remain: " + leftovers);
        }
    }

    private static String treeHash(Path root) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(value -> root.relativize(value).toString())).toList()) {
                digest.update(root.relativize(path).toString().replace('\\', '/')
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(path));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path copy = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(copy);
                else Files.copy(path, copy);
            }
        }
    }

    private static Path locateRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("repository root not found");
        return current;
    }
}
