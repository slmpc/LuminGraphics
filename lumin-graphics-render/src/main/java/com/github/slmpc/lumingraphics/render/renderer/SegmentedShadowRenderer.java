package com.github.slmpc.lumingraphics.render.renderer;

import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.lumingraphics.render.immediate.VertexBatch;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSet;

public final class SegmentedShadowRenderer extends AbstractRenderer<Render2DCommand.SegmentedShadow> {
    public SegmentedShadowRenderer(RenderResources resources, int capacity) {
        super(Render2DCommand.SegmentedShadow.class, resources, capacity, "segmented-shadow");
    }

    @Override
    protected VertexBatch vertices(Render2DCommand.SegmentedShadow command) {
        return VertexBatches.segmentedShadow(command);
    }

    @Override
    protected RhiDescriptorSet descriptor(Render2DCommand.SegmentedShadow command,
                                          RenderExecution execution) {
        return execution.resources().requireSegmentedShadowDescriptor(command);
    }
}
