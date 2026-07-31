package com.github.slmpc.lumingraphics.render.renderer;

import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;

import java.util.EnumMap;
import java.util.List;

/** Injected renderer collection with paired frame cleanup across every failure path. */
public final class RendererSet implements AutoCloseable {
    private final EnumMap<com.github.slmpc.lumingraphics.render.scheduler.Render2DCommandKind, Renderer> renderers;

    private RendererSet(EnumMap<com.github.slmpc.lumingraphics.render.scheduler.Render2DCommandKind, Renderer> renderers) {
        this.renderers = renderers;
    }

    public static RendererSet create(RenderResources resources, int capacityPerRenderer) {
        if (resources == null || capacityPerRenderer <= 0) throw new IllegalArgumentException("renderer set inputs are invalid");
        var map = new EnumMap<com.github.slmpc.lumingraphics.render.scheduler.Render2DCommandKind, Renderer>(
                com.github.slmpc.lumingraphics.render.scheduler.Render2DCommandKind.class);
        map.put(com.github.slmpc.lumingraphics.render.scheduler.Render2DCommandKind.SHADOW, new ShadowDispatchRenderer(resources, capacityPerRenderer));
        map.put(com.github.slmpc.lumingraphics.render.scheduler.Render2DCommandKind.ROUND_RECT, new RoundRectRenderer(resources, capacityPerRenderer));
        map.put(com.github.slmpc.lumingraphics.render.scheduler.Render2DCommandKind.ROUND_RECT_OUTLINE, new RoundRectOutlineRenderer(resources, capacityPerRenderer));
        map.put(com.github.slmpc.lumingraphics.render.scheduler.Render2DCommandKind.RECT, new RectRenderer(resources, capacityPerRenderer));
        map.put(com.github.slmpc.lumingraphics.render.scheduler.Render2DCommandKind.TRIANGLE, new TriangleRenderer(resources, capacityPerRenderer));
        map.put(com.github.slmpc.lumingraphics.render.scheduler.Render2DCommandKind.TEXTURE, new TextureRenderer(resources, capacityPerRenderer));
        map.put(com.github.slmpc.lumingraphics.render.scheduler.Render2DCommandKind.GLYPH, new GlyphBatchRenderer(resources, capacityPerRenderer));
        return new RendererSet(map);
    }

    public void beginFrame(RenderExecution execution) {
        try {
            for (Renderer renderer : renderers.values()) renderer.beginFrame(execution);
        } catch (RuntimeException failure) {
            endFrame();
            throw failure;
        }
    }

    public void render(Render2DCommand command, RenderExecution execution) {
        Renderer renderer = renderers.get(command.kind());
        if (renderer == null) throw new IllegalStateException("renderer is missing for " + command.kind());
        renderer.render(command, execution);
    }

    public void renderBatch(List<Render2DCommand> commands, RenderExecution execution) {
        if (commands == null || commands.isEmpty()) throw new IllegalArgumentException("render batch is empty");
        Renderer renderer = renderers.get(commands.get(0).kind());
        if (renderer == null) throw new IllegalStateException("renderer is missing for " + commands.get(0).kind());
        renderer.renderBatch(commands, execution);
    }

    public void endFrame() {
        for (Renderer renderer : renderers.values()) if (renderer.frameActive()) renderer.endFrame();
    }

    public boolean allFramesEnded() { return renderers.values().stream().noneMatch(Renderer::frameActive); }

    @Override public void close() {
        if (!allFramesEnded()) throw new IllegalStateException("cannot close renderers during a frame");
        RuntimeException failure = null;
        List<Renderer> values = List.copyOf(renderers.values());
        for (int i = values.size() - 1; i >= 0; i--) {
            try { values.get(i).close(); } catch (RuntimeException closeFailure) {
                if (failure == null) failure = closeFailure; else failure.addSuppressed(closeFailure);
            }
        }
        if (failure != null) throw failure;
    }
}
