package com.github.slmpc.lumingraphics.tooling;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

final class ShaderGenerationTransaction {
    static final String COMPLETE_MARKER = "generation-complete.properties";
    private static final String MANIFEST = "assets/lumin_graphics/shaders/spirv-manifest.csv";
    private static final String STAGING_PREFIX = "shaders.staging-";
    private static final String BACKUP_NAME = "shaders.backup";
    private static final ConcurrentHashMap<Path, LockEntry> JVM_LOCKS = new ConcurrentHashMap<>();

    private ShaderGenerationTransaction() {
    }

    record CompletionSpec(int sourceCount, String compiler, String target) {
    }

    @FunctionalInterface
    interface StagedTreeProducer {
        void produce(Path stagingRoot) throws Exception;
    }

    @FunctionalInterface
    interface GenerationPlanner {
        PlannedGeneration plan() throws Exception;
    }

    record PlannedGeneration(CompletionSpec spec, StagedTreeProducer producer) {
    }

    @FunctionalInterface
    interface PromotionObserver {
        void afterBackupMove() throws IOException;
    }

    static void publish(Path live, GenerationPlanner planner, PromotionObserver observer) throws Exception {
        try (TransactionLock transaction = TransactionLock.acquire(live)) {
            Path lockedLive = transaction.live();
            recoverOwned(lockedLive);
            PlannedGeneration generation = planner.plan();
            Path staging = lockedLive.resolveSibling(STAGING_PREFIX + UUID.randomUUID());
            try {
                Files.createDirectory(staging);
                generation.producer().produce(staging);
                validatePayload(staging, generation.spec());
                writeCompletionMarker(staging, generation.spec());
                validateCompletedTree(staging, generation.spec());
                promote(staging, lockedLive, observer);
            } finally {
                if (Files.exists(staging)) deleteTree(staging);
            }
        }
    }

    static void recover(Path live) throws IOException {
        try (TransactionLock transaction = TransactionLock.acquire(live)) {
            recoverOwned(transaction.live());
        }
    }

    private static void recoverOwned(Path live) throws IOException {
        Path parent = live.getParent();
        try (var children = Files.list(parent)) {
            for (Path child : children.filter(path -> path.getFileName().toString().startsWith(STAGING_PREFIX))
                    .toList()) {
                deleteTree(child);
            }
        }
        Path backup = parent.resolve(BACKUP_NAME);
        if (!Files.exists(backup)) return;
        boolean liveComplete = isCompletedTree(live);
        boolean backupComplete = isCompletedTree(backup);
        if (liveComplete) {
            deleteTree(backup);
        } else if (backupComplete) {
            if (Files.exists(live)) deleteTree(live);
            moveSameFileSystem(backup, live);
        } else {
            throw new IOException("interrupted shader generation has no completed live or backup tree");
        }
    }

    private static void promote(Path staging, Path live, PromotionObserver observer) throws IOException {
        Path backup = live.resolveSibling(BACKUP_NAME);
        boolean backupMoved = false;
        try {
            if (Files.exists(live)) {
                moveSameFileSystem(live, backup);
                backupMoved = true;
                observer.afterBackupMove();
            }
            moveSameFileSystem(staging, live);
        } catch (IOException | RuntimeException failure) {
            if (backupMoved && Files.exists(backup)) {
                if (Files.exists(live)) deleteTree(live);
                moveSameFileSystem(backup, live);
            }
            throw failure;
        }
        if (backupMoved) deleteTree(backup);
    }

    private static void validatePayload(Path root, CompletionSpec spec) throws IOException {
        Path manifestPath = root.resolve(MANIFEST);
        if (!Files.isRegularFile(manifestPath)) throw new IOException("generated shader manifest missing");
        List<String> manifest = Files.readAllLines(manifestPath, StandardCharsets.UTF_8);
        if (manifest.size() != spec.sourceCount() + 1
                || !manifest.get(0).equals("source,stage,entry,compiler,target,source_sha256,spirv_sha256")) {
            throw new IOException("generated shader manifest count or header mismatch");
        }
        Set<String> outputs = new HashSet<>();
        for (String row : manifest.subList(1, manifest.size())) {
            String[] columns = row.split(",", -1);
            if (columns.length != 7 || !columns[2].equals("main") || !columns[3].equals(spec.compiler())
                    || !columns[4].equals(spec.target()) || !isSha256(columns[5]) || !isSha256(columns[6])) {
                throw new IOException("generated shader manifest row invalid: " + row);
            }
            Path module = root.resolve("assets/lumin_graphics/shaders/spirv").resolve(columns[0] + ".spv");
            if (!outputs.add(columns[0]) || !Files.isRegularFile(module)
                    || !sha256(Files.readAllBytes(module)).equals(columns[6])) {
                throw new IOException("generated shader output hash mismatch: " + columns[0]);
            }
        }
        Path spirvRoot = root.resolve("assets/lumin_graphics/shaders/spirv");
        try (var modules = Files.walk(spirvRoot)) {
            if (modules.filter(path -> path.toString().endsWith(".spv")).count() != spec.sourceCount()) {
                throw new IOException("generated shader output count mismatch");
            }
        }
    }

    private static void writeCompletionMarker(Path root, CompletionSpec spec) throws IOException {
        String manifestHash = sha256(Files.readAllBytes(root.resolve(MANIFEST)));
        List<String> marker = List.of(
                "format=1",
                "source_count=" + spec.sourceCount(),
                "compiler=" + spec.compiler(),
                "target=" + spec.target(),
                "manifest_sha256=" + manifestHash);
        Files.write(root.resolve(COMPLETE_MARKER), marker, StandardCharsets.UTF_8);
    }

    private static void validateCompletedTree(Path root, CompletionSpec expected) throws IOException {
        Path markerPath = root.resolve(COMPLETE_MARKER);
        if (!Files.isRegularFile(markerPath)) throw new IOException("generated shader completion marker missing");
        Map<String, String> marker = new HashMap<>();
        for (String line : Files.readAllLines(markerPath, StandardCharsets.UTF_8)) {
            String[] entry = line.split("=", 2);
            if (entry.length != 2 || marker.put(entry[0], entry[1]) != null) {
                throw new IOException("generated shader completion marker invalid");
            }
        }
        int sourceCount;
        try {
            sourceCount = Integer.parseInt(marker.getOrDefault("source_count", ""));
        } catch (NumberFormatException invalid) {
            throw new IOException("generated shader completion source count invalid", invalid);
        }
        CompletionSpec actual = new CompletionSpec(sourceCount, marker.get("compiler"), marker.get("target"));
        if (!"1".equals(marker.get("format")) || actual.compiler() == null || actual.target() == null
                || expected != null && !expected.equals(actual)) {
            throw new IOException("generated shader completion options mismatch");
        }
        String manifestHash = sha256(Files.readAllBytes(root.resolve(MANIFEST)));
        if (!manifestHash.equals(marker.get("manifest_sha256"))) {
            throw new IOException("generated shader completion manifest hash mismatch");
        }
        validatePayload(root, actual);
    }

    private static boolean isCompletedTree(Path root) {
        if (!Files.isDirectory(root)) return false;
        try {
            validateCompletedTree(root, null);
            return true;
        } catch (IOException invalid) {
            return false;
        }
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
    }

    private static void moveSameFileSystem(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    private static LockEntry retainJvmLock(Path identity) {
        return JVM_LOCKS.compute(identity, (ignored, existing) -> {
            LockEntry entry = existing == null ? new LockEntry() : existing;
            entry.users++;
            return entry;
        });
    }

    private static void releaseJvmLock(Path identity, LockEntry entry) {
        JVM_LOCKS.computeIfPresent(identity, (ignored, existing) -> {
            if (existing != entry) return existing;
            existing.users--;
            return existing.users == 0 ? null : existing;
        });
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock(true);
        private int users;
    }

    private record TransactionLock(Path live, Path identity, LockEntry entry,
                                   FileChannel channel, FileLock fileLock) implements AutoCloseable {
        private static TransactionLock acquire(Path requestedLive) throws IOException {
            Path absolute = requestedLive.toAbsolutePath().normalize();
            Path parent = absolute.getParent();
            if (parent == null || absolute.getFileName() == null) {
                throw new IOException("shader output must have a parent directory: " + absolute);
            }
            Files.createDirectories(parent);
            Path live = parent.toRealPath().resolve(absolute.getFileName());
            Path lockPath = live.resolveSibling("." + live.getFileName() + ".generation.lock");
            try {
                Files.createFile(lockPath);
            } catch (FileAlreadyExistsException existing) {
                // The persistent sibling is the rendezvous point for all processes.
            }
            Path identity = lockPath.toRealPath();
            LockEntry entry = retainJvmLock(identity);
            boolean jvmLocked = false;
            FileChannel channel = null;
            try {
                entry.lock.lockInterruptibly();
                jvmLocked = true;
                channel = FileChannel.open(identity, StandardOpenOption.WRITE);
                return new TransactionLock(live, identity, entry, channel, acquireFileLock(channel));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting for shader generation lock", interrupted);
            } catch (IOException | RuntimeException failure) {
                if (channel != null) channel.close();
                throw failure;
            } finally {
                if (!jvmLocked) releaseJvmLock(identity, entry);
                else if (channel == null || !channel.isOpen()) {
                    entry.lock.unlock();
                    releaseJvmLock(identity, entry);
                }
            }
        }

        private static FileLock acquireFileLock(FileChannel channel) throws IOException {
            try {
                return channel.lock();
            } catch (OverlappingFileLockException overlap) {
                throw new IOException("overlapping shader lock bypassed the JVM path gate", overlap);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                fileLock.close();
            } finally {
                try {
                    channel.close();
                } finally {
                    entry.lock.unlock();
                    releaseJvmLock(identity, entry);
                }
            }
        }
    }
}
