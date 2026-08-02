package com.github.slmpc.lumingraphics.render.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 将 2D 命令规划为尽量连续的 pipeline/batch 提交顺序。
 *
 * <p>本实现迁移自 Epsilon 26.1.2 的 UiTree 合批系统。它不再为每个图元建立空间依赖，
 * 而是先在手动 layer 内把兼容命令压缩成少量 {@link BatchGroup}，再只对批次组建立遮挡
 * 依赖。典型 UI 中命令数可能达到数千，但 layer、pipeline、scissor 与 Atlas 的组合通常
 * 只有几十个，因此规划成本从图元规模转移到了批次组规模。</p>
 *
 * <p>整数 layer 是显式的顺序表达：同一 layer 内优先按 pipeline 合批；确实需要保持
 * 前后遮挡关系的元素必须放到不同 layer。不同 layer 的组只有在包围盒相交时才保持
 * layer 顺序，互不相交的 Panel、背景和文字仍可跨 layer 合并，从而减少 pipeline 切换。</p>
 *
 * <p>精确批次键由 pipeline、scissor 和采样纹理组成。字体的每一张 Atlas 页面拥有不同
 * 纹理键，所以不会把跨 Atlas 的 glyph 错误写入同一次 draw。分段阴影携带每条命令独有
 * 的结构化数据，始终作为独立批次处理。</p>
 */
final class Render2DBatchPlanner {
    private static final int PIPELINE_COUNT = PipelineKey.values().length;
    private static final Comparator<BatchGroup> LAYER_GROUP_ORDER = Comparator
            .comparingInt((BatchGroup group) -> group.key().pipeline().ordinal())
            .thenComparingLong(BatchGroup::firstSequence);

    private Render2DBatchPlanner() { }

    static List<Render2DCommand> plan(List<Render2DCommand> commands, Render2DBounds viewport,
                                      int spatialIndexThreshold) {
        if (commands.isEmpty()) return List.of();

        // TreeMap 只排序实际出现的 layer；每个 layer 内的 ArrayList 保留全局提交顺序。
        // spatialIndexThreshold 保留在公共调度器契约中，但本算法不再按图元构造空间索引。
        Map<Integer, List<Render2DCommand>> layers = new TreeMap<>();
        for (Render2DCommand command : commands) {
            if (!command.bounds().intersects(viewport)) continue;
            layers.computeIfAbsent(command.layer(), ignored -> new ArrayList<>()).add(command);
        }
        if (layers.isEmpty()) return List.of();

        List<BatchGroup> groups = new ArrayList<>();
        for (List<Render2DCommand> layer : layers.values()) {
            groups.addAll(groupLayer(layer));
        }
        return flatten(schedule(groups));
    }

    /**
     * 同一手动 layer 内按精确批次键聚合。
     *
     * <p>LinkedHashMap 保留同键命令的提交顺序；随后按 pipeline 的固定顺序排列组，
     * 使一个 layer 通常只为每种 pipeline 产生一次连续提交。若调用方需要表达同层中的
     * “背景 -> 文字 -> 前景”，应把三者放入递增 layer，而不是依赖插入顺序。</p>
     */
    private static List<BatchGroup> groupLayer(List<Render2DCommand> commands) {
        Map<BatchKey, BatchGroup> grouped = new LinkedHashMap<>();
        List<BatchGroup> isolated = new ArrayList<>();
        for (Render2DCommand command : commands) {
            BatchKey key = batchKey(command);
            if (command instanceof Render2DCommand.SegmentedShadow) {
                BatchGroup group = new BatchGroup(key);
                group.add(command);
                isolated.add(group);
            } else {
                grouped.computeIfAbsent(key, BatchGroup::new).add(command);
            }
        }
        List<BatchGroup> result = new ArrayList<>(grouped.size() + isolated.size());
        result.addAll(grouped.values());
        result.addAll(isolated);
        result.sort(LAYER_GROUP_ORDER);
        return result;
    }

    /**
     * 只在批次组之间建立 painter-order 依赖，再执行 pipeline 感知的拓扑调度。
     *
     * <p>组在输入中已经按 layer 排好。较早组与较晚组相交时添加有向边；不相交则允许
     * 重排。由于一个组的 bounds 是其全部命令 bounds 的并集，可能产生少量保守依赖，
     * 但不会漏掉真正的遮挡关系。复杂度为 O(G^2 + G * P)，G 是批次组数而非图元数，
     * P 是固定 pipeline 数量。</p>
     */
    private static List<BatchGroup> schedule(List<BatchGroup> groups) {
        int count = groups.size();
        if (count <= 1) return groups;

        DependencyGraph graph = new DependencyGraph(count);
        for (int later = 0; later < count; later++) {
            Render2DBounds laterBounds = groups.get(later).bounds();
            for (int earlier = 0; earlier < later; earlier++) {
                if (groups.get(earlier).bounds().intersects(laterBounds)) {
                    graph.add(earlier, later);
                }
            }
        }

        boolean[] scheduled = new boolean[count];
        int[] readyPipelines = new int[PIPELINE_COUNT];
        for (int index = 0; index < count; index++) {
            if (graph.indegree(index) == 0) readyPipelines[groups.get(index).key().pipeline().ordinal()]++;
        }

        List<BatchGroup> result = new ArrayList<>(count);
        BatchKey preferredBatch = null;
        PipelineKey preferredPipeline = null;
        for (int emitted = 0; emitted < count; emitted++) {
            int selected = selectReady(groups, graph, scheduled, readyPipelines,
                    preferredBatch, preferredPipeline);
            if (selected < 0) throw new IllegalStateException("2D batch dependency cycle");

            BatchGroup group = groups.get(selected);
            scheduled[selected] = true;
            readyPipelines[group.key().pipeline().ordinal()]--;
            result.add(group);
            preferredBatch = group.key();
            preferredPipeline = group.key().pipeline();

            for (int edge = graph.firstEdge(selected); edge >= 0; edge = graph.nextEdge(edge)) {
                int successor = graph.successor(edge);
                if (graph.release(successor) == 0) {
                    readyPipelines[groups.get(successor).key().pipeline().ordinal()]++;
                }
            }
        }
        return result;
    }

    private static int selectReady(List<BatchGroup> groups, DependencyGraph graph, boolean[] scheduled,
                                   int[] readyPipelines, BatchKey preferredBatch,
                                   PipelineKey preferredPipeline) {
        int samePipeline = -1;
        int fallback = -1;
        int largestPipeline = largestReadyPipeline(readyPipelines);
        for (int index = 0; index < groups.size(); index++) {
            if (scheduled[index] || graph.indegree(index) != 0) continue;
            BatchKey key = groups.get(index).key();
            if (preferredBatch != null && preferredBatch.equals(key)) return index;
            if (samePipeline < 0 && key.pipeline() == preferredPipeline) samePipeline = index;
            if (fallback < 0 && key.pipeline().ordinal() == largestPipeline) fallback = index;
        }
        return samePipeline >= 0 ? samePipeline : fallback;
    }

    private static int largestReadyPipeline(int[] readyPipelines) {
        int selected = -1;
        int largest = 0;
        for (int pipeline = 0; pipeline < readyPipelines.length; pipeline++) {
            if (readyPipelines[pipeline] > largest) {
                largest = readyPipelines[pipeline];
                selected = pipeline;
            }
        }
        return selected;
    }

    private static List<Render2DCommand> flatten(List<BatchGroup> groups) {
        int commandCount = 0;
        for (BatchGroup group : groups) commandCount += group.commands().size();
        List<Render2DCommand> result = new ArrayList<>(commandCount);
        for (BatchGroup group : groups) result.addAll(group.commands());
        return List.copyOf(result);
    }

    private static BatchKey batchKey(Render2DCommand command) {
        return new BatchKey(pipeline(command), command.scissor(), texture(command));
    }

    private static PipelineKey pipeline(Render2DCommand command) {
        if (command instanceof Render2DCommand.SegmentedShadow) return PipelineKey.SEGMENTED_SHADOW;
        return switch (command.kind()) {
            case SHADOW -> PipelineKey.SHADOW;
            case ROUND_RECT -> PipelineKey.ROUND_RECT;
            case ROUND_RECT_OUTLINE -> PipelineKey.ROUND_RECT_OUTLINE;
            case RECT -> PipelineKey.RECT;
            case TRIANGLE -> PipelineKey.TRIANGLE;
            case TEXTURE -> PipelineKey.TEXTURE;
            case GLYPH -> PipelineKey.GLYPH;
        };
    }

    private static Render2DTexture texture(Render2DCommand command) {
        if (command instanceof Render2DCommand.Texture value) return value.texture();
        if (command instanceof Render2DCommand.Glyphs value) return value.texture();
        return null;
    }

    private enum PipelineKey {
        SHADOW,
        SEGMENTED_SHADOW,
        ROUND_RECT,
        ROUND_RECT_OUTLINE,
        RECT,
        TRIANGLE,
        TEXTURE,
        GLYPH
    }

    private record BatchKey(PipelineKey pipeline, Render2DScissor scissor, Render2DTexture texture) {
        private BatchKey {
            Objects.requireNonNull(pipeline, "pipeline");
        }
    }

    private static final class BatchGroup {
        private final BatchKey key;
        private final List<Render2DCommand> commands = new ArrayList<>();
        private Render2DBounds bounds;

        private BatchGroup(BatchKey key) { this.key = key; }

        private void add(Render2DCommand command) {
            commands.add(command);
            Render2DBounds commandBounds = command.bounds();
            bounds = bounds == null ? commandBounds : include(bounds, commandBounds);
        }

        private BatchKey key() { return key; }

        private List<Render2DCommand> commands() { return commands; }

        private Render2DBounds bounds() { return bounds; }

        private long firstSequence() { return commands.get(0).sequence(); }
    }

    /** primitive 前向星邻接表；组级图无需为每条边分配 List/Integer 对象。 */
    private static final class DependencyGraph {
        private final int[] firstEdges;
        private final int[] indegrees;
        private int[] successors;
        private int[] nextEdges;
        private int edgeCount;

        private DependencyGraph(int size) {
            firstEdges = new int[size];
            java.util.Arrays.fill(firstEdges, -1);
            indegrees = new int[size];
            int capacity = Math.max(16, size * 2);
            successors = new int[capacity];
            nextEdges = new int[capacity];
        }

        private void add(int earlier, int later) {
            ensureCapacity();
            successors[edgeCount] = later;
            nextEdges[edgeCount] = firstEdges[earlier];
            firstEdges[earlier] = edgeCount++;
            indegrees[later]++;
        }

        private void ensureCapacity() {
            if (edgeCount < successors.length) return;
            int expanded = Math.addExact(successors.length, Math.max(1, successors.length >>> 1));
            successors = java.util.Arrays.copyOf(successors, expanded);
            nextEdges = java.util.Arrays.copyOf(nextEdges, expanded);
        }

        private int indegree(int group) { return indegrees[group]; }

        private int release(int group) { return --indegrees[group]; }

        private int firstEdge(int group) { return firstEdges[group]; }

        private int nextEdge(int edge) { return nextEdges[edge]; }

        private int successor(int edge) { return successors[edge]; }
    }

    private static Render2DBounds include(Render2DBounds first, Render2DBounds second) {
        float left = Math.min(first.x(), second.x());
        float top = Math.min(first.y(), second.y());
        float right = Math.max(first.right(), second.right());
        float bottom = Math.max(first.bottom(), second.bottom());
        return new Render2DBounds(left, top, right - left, bottom - top);
    }
}
