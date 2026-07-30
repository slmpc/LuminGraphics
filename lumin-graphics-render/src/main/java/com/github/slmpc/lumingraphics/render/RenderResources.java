package com.github.slmpc.lumingraphics.render;

import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSet;
import com.github.slmpc.prismrhi.device.RhiDevice;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipeline;

/** Explicit resource services supplied by an application or backend adapter. */
public interface RenderResources {
    RhiDevice device();
    RhiGraphicsPipeline requirePipeline(String id);
    RhiDescriptorSet requireTextureDescriptor(Render2DTexture texture);
}
