package com.github.slmpc.lumingraphics.ui.render;

import com.github.slmpc.lumingraphics.render.scheduler.GlyphQuad;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DBounds;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScheduler;
import com.github.slmpc.lumingraphics.text.render.TextBatchSink;
import com.github.slmpc.lumingraphics.text.render.TextDraw;
import com.github.slmpc.lumingraphics.ui.resource.UiResourceResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SchedulerTextBatchSink implements TextBatchSink {
    private final UiResourceResolver resources;
    private Render2DScheduler.LayerHandle target;
    private UiRenderBatch owner;

    public SchedulerTextBatchSink(UiResourceResolver resources) {
        this.resources = Objects.requireNonNull(resources);
    }

    public void bind(Render2DScheduler.LayerHandle value, UiRenderBatch batch) {
        target = Objects.requireNonNull(value, "value");
        owner = Objects.requireNonNull(batch, "batch");
    }

    @Override
    public void draw(List<TextDraw> draws) {
        Render2DScheduler.LayerHandle layer = Objects.requireNonNull(target, "text target is not bound");
        UiRenderBatch leaseOwner = Objects.requireNonNull(owner, "text owner is not bound");
        for (TextDraw borrowedDraw : draws) {
            TextDraw draw = borrowedDraw.retain();
            leaseOwner.retainTextDraw(draw);
            for (var batch : draw.batches()) {
                List<GlyphQuad> glyphs = new ArrayList<>();
                for (var glyph : batch.glyphs()) {
                    glyphs.add(new GlyphQuad(
                            new Render2DBounds(glyph.x0(), glyph.y0(), glyph.x1() - glyph.x0(), glyph.y1() - glyph.y0()),
                            glyph.uv().u0(), glyph.uv().v0(), glyph.uv().u1(), glyph.uv().v1(), draw.color()));
                }
                if (glyphs.isEmpty()) continue;
                Render2DBounds bounds = new Render2DBounds(draw.x(), draw.y(), draw.width(), draw.height());
                if (draw.rotationDegrees() == 0) {
                    layer.addGlyphs(bounds, resources.atlasTexture(batch.upload().texture()), glyphs);
                } else {
                    layer.addRotatedGlyphs(bounds, resources.atlasTexture(batch.upload().texture()), glyphs,
                            draw.originX(), draw.originY(), draw.rotationDegrees());
                }
            }
        }
    }
}

