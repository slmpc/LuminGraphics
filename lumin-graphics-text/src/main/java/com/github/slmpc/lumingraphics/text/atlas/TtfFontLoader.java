package com.github.slmpc.lumingraphics.text.atlas;

import com.github.slmpc.lumingraphics.text.font.FontClosedException;
import com.github.slmpc.lumingraphics.text.font.FontLoader;
import com.github.slmpc.lumingraphics.text.font.FontMetrics;
import com.github.slmpc.lumingraphics.text.font.FontResource;
import com.github.slmpc.lumingraphics.text.ttf.TtfFontFile;
import com.github.slmpc.lumingraphics.text.ttf.TtfGlyph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 将调用方提供的 TTF/OTF 字体按需栅格化到 GPU 图集页。
 *
 * <p>同一 code point 的并发请求会合并为一次栅格化。关闭时未完成请求会被取消，所有图集页和字体文件
 * 都会被关闭。上传器必须遵守调用方的图形线程契约。</p>
 */
public final class TtfFontLoader implements FontLoader {
    private final TtfFontFile font;
    private final int atlasWidth;
    private final int atlasHeight;
    private final int maxAtlasPages;
    private final GlyphAtlasUploader uploader;
    private final Executor executor;
    private final Map<Integer, GlyphDescriptor> glyphs = new LinkedHashMap<>();
    private final Map<Integer, PendingRequest> pending = new ConcurrentHashMap<>();
    private final List<TtfGlyphAtlas> atlases = new ArrayList<>();
    private final AtomicInteger rasterizationCount = new AtomicInteger();
    private long glyphRevision;
    private long atlasRevision;
    private boolean closed;

    /**
     * 创建字体 loader。
     *
     * @param resource      调用方提供的字体字节来源
     * @param pixelHeight   栅格化像素高度
     * @param padding       每个字形的额外像素边距
     * @param atlasWidth    图集页宽度
     * @param atlasHeight   图集页高度
     * @param maxAtlasPages 最大图集页数
     * @param uploader      将图集像素上传到后端的实现
     * @param executor      执行栅格化工作的执行器
     */
    public TtfFontLoader(FontResource resource, int pixelHeight, int padding, int atlasWidth, int atlasHeight,
                         int maxAtlasPages, GlyphAtlasUploader uploader, Executor executor) {
        if (maxAtlasPages <= 0) throw new IllegalArgumentException("maxAtlasPages must be positive");
        this.font = TtfFontFile.open(resource, pixelHeight, padding);
        this.atlasWidth = atlasWidth;
        this.atlasHeight = atlasHeight;
        this.maxAtlasPages = maxAtlasPages;
        this.uploader = Objects.requireNonNull(uploader, "uploader");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * 异步请求一个字形；订阅者可独立取消，重复请求会共享同一栅格化工作。
     */
    @Override
    public synchronized CompletableFuture<GlyphDescriptor> requestGlyph(int codepoint) {
        ensureOpen();
        GlyphDescriptor loaded = glyphs.get(codepoint);
        if (loaded != null) return CompletableFuture.completedFuture(loaded);
        PendingRequest existing = pending.get(codepoint);
        if (existing != null) return existing.subscribe();
        PendingRequest request = new PendingRequest(codepoint);
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                if (Thread.currentThread().isInterrupted()) throw new CancellationException();
                rasterizationCount.incrementAndGet();
                TtfGlyph glyph = font.rasterize(codepoint);
                if (request.isCancelled()) throw new CancellationException();
                request.complete(appendGlyph(glyph, request));
            } catch (CancellationException ignored) {
                // The cancelling subscriber already owns its cancellation state.
            } catch (Throwable error) {
                request.completeExceptionally(error);
            } finally {
                removePending(codepoint, request);
            }
            return null;
        });
        request.task = task;
        CompletableFuture<GlyphDescriptor> subscriber = request.subscribe();
        pending.put(codepoint, request);
        try {
            executor.execute(task);
        } catch (RuntimeException error) {
            pending.remove(codepoint);
            request.completeExceptionally(error);
        }
        return subscriber;
    }

    private void removePending(int codepoint, PendingRequest request) {
        pending.remove(codepoint, request);
    }

    private synchronized GlyphDescriptor appendGlyph(TtfGlyph glyph, PendingRequest request) {
        ensureOpen();
        if (request.isCancelled()) throw new CancellationException();
        try {
            GlyphDescriptor existing = glyphs.get(glyph.codepoint());
            if (existing != null) {
                if (!request.beginCommit()) throw new CancellationException();
                return existing;
            }
            TtfGlyphAtlas target = atlases.isEmpty() ? createAtlas() : atlases.get(atlases.size() - 1);
            GlyphUv uv = target.append(glyph, request::beginCommit);
            if (uv == null) {
                if (request.isCancelled()) throw new CancellationException();
                if (atlases.size() >= maxAtlasPages) {
                    throw new AtlasExhaustedException("Glyph atlas exhausted after " + maxAtlasPages + " pages");
                }
                target = createAtlas();
                uv = target.append(glyph, request::beginCommit);
                if (request.isCancelled()) throw new CancellationException();
                if (uv == null) throw new AtlasExhaustedException("Glyph U+" + Integer.toHexString(glyph.codepoint()) +
                        " is larger than an atlas page");
            }
            GlyphDescriptor descriptor = new GlyphDescriptor(glyph.codepoint(), target, uv, glyph.width(), glyph.height(),
                    glyph.xOffset(), glyph.yOffset(), glyph.advance());
            glyphs.put(glyph.codepoint(), descriptor);
            glyphRevision++;
            atlasRevision++;
            return descriptor;
        } finally {
            request.finishCommit();
        }
    }

    private TtfGlyphAtlas createAtlas() {
        TtfGlyphAtlas atlas = new TtfGlyphAtlas(atlases.size(), atlasWidth, atlasHeight, uploader);
        atlases.add(atlas);
        return atlas;
    }

    /**
     * 同步取得字形；底层异步失败会按原有运行时异常传播。
     */
    @Override
    public GlyphDescriptor requireGlyph(int codepoint) {
        try {
            return requestGlyph(codepoint).join();
        } catch (CompletionException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw error;
        }
    }

    @Override
    public synchronized int advance(int codepoint) {
        ensureOpen();
        return font.advance(codepoint);
    }

    @Override
    public synchronized int kerning(int left, int right) {
        ensureOpen();
        return font.kerning(left, right);
    }

    @Override
    public synchronized FontMetrics metrics() {
        ensureOpen();
        return font.metrics();
    }

    @Override
    public synchronized long glyphRevision() {
        ensureOpen();
        return glyphRevision;
    }

    @Override
    public synchronized long atlasRevision() {
        ensureOpen();
        return atlasRevision;
    }

    public int rasterizationCount() {
        return rasterizationCount.get();
    }

    private void ensureOpen() {
        if (closed) throw new FontClosedException("Font loader is closed");
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        List<PendingRequest> pendingSnapshot = List.copyOf(pending.values());
        pending.clear();
        List<TtfGlyphAtlas> atlasSnapshot = List.copyOf(atlases);
        atlases.clear();
        glyphs.clear();
        FontClosedException closedError = new FontClosedException("Font loader closed during glyph request");
        pendingSnapshot.forEach(request -> request.close(closedError));
        RuntimeException failure = null;
        for (int index = atlasSnapshot.size() - 1; index >= 0; index--) {
            try {
                atlasSnapshot.get(index).close();
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        try {
            font.close();
        } catch (RuntimeException error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
    }

    private final class PendingRequest {
        private final int codepoint;
        private final List<SubscriberFuture> subscribers = new ArrayList<>();
        private final ReentrantLock commitLock = new ReentrantLock();
        private FutureTask<Void> task;
        private boolean committed;
        private boolean terminal;
        private GlyphDescriptor terminalGlyph;
        private Throwable terminalError;

        private PendingRequest(int codepoint) {
            this.codepoint = codepoint;
        }

        CompletableFuture<GlyphDescriptor> subscribe() {
            commitLock.lock();
            try {
                SubscriberFuture subscriber = new SubscriberFuture(this);
                if (terminalError != null) subscriber.completeExceptionally(terminalError);
                else if (terminal) subscriber.complete(terminalGlyph);
                else subscribers.add(subscriber);
                return subscriber;
            } finally {
                commitLock.unlock();
            }
        }

        boolean isCancelled() {
            return task.isCancelled();
        }

        boolean beginCommit() {
            commitLock.lock();
            if (task.isCancelled() || subscribers.isEmpty()) {
                commitLock.unlock();
                return false;
            }
            committed = true;
            return true;
        }

        void finishCommit() {
            if (commitLock.isHeldByCurrentThread()) commitLock.unlock();
        }

        void complete(GlyphDescriptor glyph) {
            commitLock.lock();
            try {
                terminal = true;
                terminalGlyph = glyph;
                List.copyOf(subscribers).forEach(subscriber -> subscriber.complete(glyph));
                subscribers.clear();
            } finally {
                commitLock.unlock();
            }
        }

        void completeExceptionally(Throwable error) {
            commitLock.lock();
            try {
                terminal = true;
                terminalError = error;
                List.copyOf(subscribers).forEach(subscriber -> subscriber.completeExceptionally(error));
                subscribers.clear();
            } finally {
                commitLock.unlock();
            }
        }

        boolean cancel(SubscriberFuture subscriber, boolean mayInterrupt) {
            commitLock.lock();
            try {
                if (committed || !subscriber.cancelDirect(mayInterrupt)) return false;
                subscribers.remove(subscriber);
                if (subscribers.isEmpty()) {
                    task.cancel(mayInterrupt);
                    removePending(codepoint, this);
                }
                return true;
            } finally {
                commitLock.unlock();
            }
        }

        void close(FontClosedException error) {
            completeExceptionally(error);
            task.cancel(true);
        }
    }

    private static final class SubscriberFuture extends CompletableFuture<GlyphDescriptor> {
        private final PendingRequest owner;

        private SubscriberFuture(PendingRequest owner) {
            this.owner = owner;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return owner.cancel(this, mayInterruptIfRunning);
        }

        private boolean cancelDirect(boolean mayInterruptIfRunning) {
            return super.cancel(mayInterruptIfRunning);
        }
    }
}
