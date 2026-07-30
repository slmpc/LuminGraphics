package com.github.slmpc.lumingraphics.render.scheduler;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.lumingraphics.render.RenderExecution;
import com.github.slmpc.lumingraphics.render.renderer.RendererSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Render2DScheduler implements AutoCloseable {
    private static final Comparator<Render2DCommand> ORDER = Comparator.comparingInt(Render2DCommand::layer)
            .thenComparingLong(Render2DCommand::sequence);
    private final RendererSet renderers;
    private final int quadtreeThreshold;
    private final List<Render2DCommand> commands = new ArrayList<>();
    private final Map<Integer, LayerHandle> layers = new HashMap<>();
    private final java.util.Set<Long> flushedSequences = new HashSet<>();
    private long sequence;
    private boolean closed;

    public Render2DScheduler(RendererSet renderers, int quadtreeThreshold) {
        if (renderers == null || quadtreeThreshold <= 0) throw new IllegalArgumentException("scheduler inputs are invalid");
        this.renderers = renderers;
        this.quadtreeThreshold = quadtreeThreshold;
    }
    public LayerHandle layer(int layer) { requireOpen(); return layers.computeIfAbsent(layer, key -> new LayerHandle(key)); }
    public boolean isEmpty() { return commands.isEmpty(); }
    public void clear() { requireOpen(); commands.clear(); flushedSequences.clear(); layers.values().forEach(LayerHandle::clearScissors); sequence = 0; }

    public void clearLayer(int layer) {
        requireOpen();
        commands.removeIf(command -> {
            if (command.layer() != layer) return false;
            flushedSequences.remove(command.sequence());
            return true;
        });
        LayerHandle handle = layers.get(layer);
        if (handle != null) handle.clearScissors();
    }

    public void flush(RenderExecution execution) {
        requireOpen();
        if (commands.isEmpty()) return;
        List<Render2DCommand> pending = commands.stream()
                .filter(command -> !flushedSequences.contains(command.sequence())).toList();
        if (pending.isEmpty()) throw new IllegalStateException("scheduler commands were already flushed");
        flushedSequences.addAll(pending.stream().map(Render2DCommand::sequence).toList());
        render(execution, pending);
    }

    public void flushLayer(int layer, RenderExecution execution) {
        requireOpen();
        List<Render2DCommand> pending = commands.stream()
                .filter(command -> command.layer() == layer && !flushedSequences.contains(command.sequence())).toList();
        if (pending.isEmpty()) {
            if (commands.stream().anyMatch(command -> command.layer() == layer))
                throw new IllegalStateException("scheduler layer was already flushed");
            return;
        }
        flushedSequences.addAll(pending.stream().map(Render2DCommand::sequence).toList());
        render(execution, pending);
    }

    private void render(RenderExecution execution, List<Render2DCommand> pending) {
        Render2DBounds viewport = new Render2DBounds(0, 0, execution.width(), execution.height());
        List<Render2DCommand> ordered = pending.stream().sorted(ORDER).toList();
        if (ordered.size() >= quadtreeThreshold) {
            var visible = new Render2DQuadTree(viewport, ordered).query(viewport);
            ordered = ordered.stream().filter(visible::contains).toList();
        } else {
            ordered = ordered.stream().filter(command -> command.bounds().intersects(viewport)).toList();
        }
        if (ordered.isEmpty()) return;
        renderers.beginFrame(execution);
        try {
            for (int start = 0; start < ordered.size();) {
                Render2DCommand command = ordered.get(start);
                int end = start + 1;
                while (end < ordered.size() && batchCompatible(command, ordered.get(end))) end++;
                Render2DScissor framebuffer = new Render2DScissor(0, 0, execution.width(), execution.height());
                execution.commands().setScissor(command.scissor() == null
                        ? framebuffer.toRhi() : framebuffer.intersect(command.scissor()).toRhi());
                renderers.renderBatch(ordered.subList(start, end), execution);
                start = end;
            }
        } finally {
            renderers.endFrame();
        }
    }

    public void flushAndClear(RenderExecution execution) {
        try { flush(execution); } finally { clear(); }
    }

    private void add(Render2DCommand command) { commands.add(command); }
    private static boolean batchCompatible(Render2DCommand left, Render2DCommand right) {
        return left.kind() == right.kind() && Objects.equals(left.scissor(), right.scissor())
                && Objects.equals(texture(left), texture(right));
    }
    private static Render2DTexture texture(Render2DCommand command) {
        if (command instanceof Render2DCommand.Texture textured) return textured.texture();
        if (command instanceof Render2DCommand.Glyphs glyphs) return glyphs.texture();
        return null;
    }
    private long nextSequence() { return sequence++; }
    private void requireOpen() { if (closed) throw new IllegalStateException("scheduler is closed"); }

    @Override public void close() {
        if (closed) return;
        clear();
        closed = true;
        renderers.close();
    }

    public final class LayerHandle {
        private final int layer;
        private final Deque<Render2DScissor> scissors = new ArrayDeque<>();
        private LayerHandle(int layer) { this.layer = layer; }
        public void pushScissor(Render2DScissor scissor) {
            requireOpen(); if (scissor == null) throw new IllegalArgumentException("scissor is null");
            scissors.push(scissors.isEmpty() ? scissor : scissors.peek().intersect(scissor));
        }
        public void popScissor() { requireOpen(); if (scissors.isEmpty()) throw new IllegalStateException("scissor stack is empty"); scissors.pop(); }
        public void clearScissors() { scissors.clear(); }
        private Render2DScissor scissor() { return scissors.peek(); }
        public void addRect(Render2DBounds bounds, LuminColor color) { add(new Render2DCommand.Rect(layer, nextSequence(), bounds, scissor(), color)); }
        public void addRoundRect(Render2DBounds b, float radius, LuminColor color) { add(new Render2DCommand.RoundRect(layer, nextSequence(), b, scissor(), radius, color)); }
        public void addOutline(Render2DBounds b, float radius, float width, LuminColor color) { add(new Render2DCommand.RoundRectOutline(layer, nextSequence(), b, scissor(), radius, width, color)); }
        public void addShadow(Render2DBounds b, float radius, float blur, LuminColor color) { add(new Render2DCommand.Shadow(layer, nextSequence(), b, scissor(), radius, blur, color)); }
        public void addTexture(Render2DBounds b, Render2DTexture texture, LuminColor color) { add(new Render2DCommand.Texture(layer, nextSequence(), b, scissor(), texture, color)); }
        public void addGlyphs(Render2DBounds b, Render2DTexture texture, List<GlyphQuad> glyphs) { add(new Render2DCommand.Glyphs(layer, nextSequence(), b, scissor(), texture, glyphs)); }
        public void addTriangle(float cx, float cy, float size, LuminColor color) {
            Render2DBounds b = new Render2DBounds(cx - size, cy - size, size * 2, size * 2);
            add(new Render2DCommand.Triangle(layer, nextSequence(), b, scissor(), cx - size, cy - size,
                    cx - size, cy + size, cx + size, cy, color));
        }
    }
}
