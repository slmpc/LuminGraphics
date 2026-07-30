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
        return left.kind() == right.kind() && !(left instanceof Render2DCommand.SegmentedShadow)
                && !(right instanceof Render2DCommand.SegmentedShadow)
                && Objects.equals(left.scissor(), right.scissor())
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
        public Render2DScissor scissor() { return scissors.peek(); }
        public void addRect(Render2DBounds bounds, LuminColor color) { add(new Render2DCommand.Rect(layer, nextSequence(), bounds, scissor(), color)); }
        public void addRectGradient(Render2DBounds bounds, LuminColor topLeft, LuminColor bottomLeft,
                                    LuminColor bottomRight, LuminColor topRight) {
            add(new Render2DCommand.Rect(layer, nextSequence(), bounds, scissor(),
                    topLeft, bottomLeft, bottomRight, topRight));
        }
        public void addRectOutline(Render2DBounds b, float width, LuminColor color) {
            if (b == null || !Float.isFinite(width) || width < 0 || width * 2 > Math.min(b.width(), b.height())
                    || color == null) {
                throw new IllegalArgumentException("rectangle outline width is outside its bounds");
            }
            if (width == 0) return;
            addRect(new Render2DBounds(b.x(), b.y(), b.width(), width), color);
            addRect(new Render2DBounds(b.x(), b.bottom() - width, b.width(), width), color);
            float sideHeight = b.height() - width * 2;
            addRect(new Render2DBounds(b.x(), b.y() + width, width, sideHeight), color);
            addRect(new Render2DBounds(b.right() - width, b.y() + width, width, sideHeight), color);
        }
        public void addRoundRect(Render2DBounds b, float radius, LuminColor color) { add(new Render2DCommand.RoundRect(layer, nextSequence(), b, scissor(), radius, color)); }
        public void addRoundRect(Render2DBounds b, float topLeft, float topRight, float bottomRight,
                                 float bottomLeft, LuminColor color) {
            addRoundRectGradient(b, topLeft, topRight, bottomRight, bottomLeft, color, color, color, color);
        }
        public void addRoundRectGradient(Render2DBounds b, float topLeftRadius, float topRightRadius,
                                         float bottomRightRadius, float bottomLeftRadius,
                                         LuminColor topLeft, LuminColor bottomLeft,
                                         LuminColor bottomRight, LuminColor topRight) {
            add(new Render2DCommand.RoundRect(layer, nextSequence(), b, scissor(),
                    topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius,
                    topLeft, bottomLeft, bottomRight, topRight));
        }
        public void addOutline(Render2DBounds b, float radius, float width, LuminColor color) { add(new Render2DCommand.RoundRectOutline(layer, nextSequence(), b, scissor(), radius, width, color)); }
        public void addOutline(Render2DBounds b, float topLeft, float topRight, float bottomRight,
                               float bottomLeft, float width, LuminColor color) {
            add(new Render2DCommand.RoundRectOutline(layer, nextSequence(), b, scissor(),
                    topLeft, topRight, bottomRight, bottomLeft, width, color));
        }
        public void addShadow(Render2DBounds b, float radius, float blur, LuminColor color) { add(new Render2DCommand.Shadow(layer, nextSequence(), b, scissor(), radius, blur, color)); }
        public void addShadow(Render2DBounds b, float topLeft, float topRight, float bottomRight,
                              float bottomLeft, float blur, LuminColor color) {
            add(new Render2DCommand.Shadow(layer, nextSequence(), b, scissor(),
                    topLeft, topRight, bottomRight, bottomLeft, blur, color));
        }
        public void addSegmentedShadow(Render2DBounds b, float topLeft, float topRight, float bottomRight,
                                       float bottomLeft, float blur, LuminColor color,
                                       float[] segmentRects, float[] segmentRadii, int segmentCount) {
            add(new Render2DCommand.SegmentedShadow(layer, nextSequence(), b, scissor(),
                    topLeft, topRight, bottomRight, bottomLeft, blur, color,
                    segmentRects, segmentRadii, segmentCount));
        }
        public void addShadow(Render2DBounds b, float topLeft, float topRight, float bottomRight,
                              float bottomLeft, float blur, LuminColor color,
                              float[] segmentRects, float[] segmentRadii, int segmentCount) {
            addSegmentedShadow(b, topLeft, topRight, bottomRight, bottomLeft, blur, color,
                    segmentRects, segmentRadii, segmentCount);
        }
        public void addTexture(Render2DBounds b, Render2DTexture texture, LuminColor color) { add(new Render2DCommand.Texture(layer, nextSequence(), b, scissor(), texture, color)); }
        public void addTexture(Render2DBounds b, Render2DTexture texture, float u0, float v0,
                               float u1, float v1, LuminColor color) {
            addRoundedTexture(b, texture, 0, 0, 0, 0, u0, v0, u1, v1, color);
        }
        public void addRoundedTexture(Render2DBounds b, Render2DTexture texture, float radius,
                                      float u0, float v0, float u1, float v1, LuminColor color) {
            addRoundedTexture(b, texture, radius, radius, radius, radius, u0, v0, u1, v1, color);
        }
        public void addRoundedTexture(Render2DBounds b, Render2DTexture texture,
                                      float topLeft, float topRight, float bottomRight, float bottomLeft,
                                      float u0, float v0, float u1, float v1, LuminColor color) {
            add(new Render2DCommand.Texture(layer, nextSequence(), b, scissor(), texture,
                    topLeft, topRight, bottomRight, bottomLeft, u0, v0, u1, v1, color,
                    0, 0, 0));
        }
        public void addRotatedTexture(Render2DBounds b, Render2DTexture texture,
                                      float u0, float v0, float u1, float v1, LuminColor color,
                                      float originX, float originY, float rotationDegrees) {
            add(new Render2DCommand.Texture(layer, nextSequence(), b, scissor(), texture,
                    0, 0, 0, 0, u0, v0, u1, v1, color, originX, originY, rotationDegrees));
        }
        public void addGlyphs(Render2DBounds b, Render2DTexture texture, List<GlyphQuad> glyphs) { add(new Render2DCommand.Glyphs(layer, nextSequence(), b, scissor(), texture, glyphs)); }
        public void addRotatedGlyphs(Render2DBounds b, Render2DTexture texture, List<GlyphQuad> glyphs,
                                     float originX, float originY, float rotationDegrees) {
            add(new Render2DCommand.Glyphs(layer, nextSequence(), b, scissor(), texture, glyphs,
                    originX, originY, rotationDegrees));
        }
        /** Emits an ordinary glyph batch under a temporary nested scissor, the exact marquee decomposition. */
        public void addMarqueeGlyphs(Render2DBounds b, Render2DScissor clip, Render2DTexture texture,
                                     List<GlyphQuad> glyphs) {
            pushScissor(clip);
            try { addGlyphs(b, texture, glyphs); } finally { popScissor(); }
        }
        public void addTriangle(float cx, float cy, float size, LuminColor color) {
            addChevronTriangle(cx, cy, size, 0, color);
        }
        public void addChevronTriangle(float cx, float cy, float size, float progress, LuminColor color) {
            Render2DBounds b = new Render2DBounds(cx - size, cy - size, size * 2, size * 2);
            add(new Render2DCommand.Triangle(layer, nextSequence(), b, scissor(),
                    cx, cy, size, progress, color));
        }
    }
}
