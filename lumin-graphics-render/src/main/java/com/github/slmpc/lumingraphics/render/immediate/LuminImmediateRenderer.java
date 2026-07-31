package com.github.slmpc.lumingraphics.render.immediate;

import com.github.slmpc.lumingraphics.core.buffer.LuminRingBuffer;
import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSet;

/** Uploads immediate CPU vertex batches and records explicit Prism draw commands. */
public final class LuminImmediateRenderer implements AutoCloseable {
    private final LuminRingBuffer ring;

    public LuminImmediateRenderer(RenderResources resources, int bytesPerSlot) {
        if (resources == null) throw new IllegalArgumentException("render resources must not be null");
        ring = new LuminRingBuffer(resources.device(), bytesPerSlot, 3);
    }

    public void beginFrame(RenderExecution execution) {
        ring.beginFrame(execution.frameId(), execution.completedFrameId());
    }

    public void draw(VertexBatch batch, String pipelineId, Render2DTexture texture, RenderExecution execution) {
        draw(batch, pipelineId, texture, null, execution);
    }

    public void drawWithDescriptor(VertexBatch batch, String pipelineId, RhiDescriptorSet descriptor,
                                   RenderExecution execution) {
        draw(batch, pipelineId, null, descriptor, execution);
    }

    private void draw(VertexBatch batch, String pipelineId, Render2DTexture texture,
                      RhiDescriptorSet descriptor, RenderExecution execution) {
        if (!ring.frameActive()) throw new IllegalStateException("immediate renderer frame is not active");
        var allocation = ring.write(batch.bytes(), 16);
        var pipeline = execution.resources().requirePipeline(pipelineId);
        execution.commands().bindGraphicsPipeline(pipeline);
        if (texture != null) descriptor = execution.resources().requireTextureDescriptor(texture);
        if (descriptor != null) execution.commands().bindDescriptorSet(pipeline, 0, descriptor);
        execution.commands().bindVertexBuffer(0, allocation.buffer(), allocation.offset());
        execution.commands().draw(batch.vertexCount());
    }

    public void endFrame() {
        if (ring.frameActive()) ring.endFrame();
    }

    public boolean frameActive() { return ring.frameActive(); }

    @Override public void close() { ring.close(); }
}
