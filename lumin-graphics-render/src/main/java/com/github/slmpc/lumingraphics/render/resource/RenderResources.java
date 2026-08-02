package com.github.slmpc.lumingraphics.render.resource;

import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.shader.FullscreenEffectBinding;
import com.github.slmpc.lumingraphics.render.shader.FullscreenEffectRequest;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSet;
import com.github.slmpc.prismrhi.device.RhiDevice;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipeline;

/** Explicit resource services supplied by an application or backend adapter. */
public interface RenderResources {
    RhiDevice device();
    RhiGraphicsPipeline requirePipeline(String id);
    /** Resolves the shared per-frame uniform descriptor for untextured 2D draws. */
    RhiDescriptorSet requireFrameDescriptor();
    RhiDescriptorSet requireTextureDescriptor(Render2DTexture texture);
    /** Resolves the exact per-draw union-of-rounded-segments payload for the segmented-shadow pipeline. */
    RhiDescriptorSet requireSegmentedShadowDescriptor(Render2DCommand.SegmentedShadow shadow);
    /** Resolves the combined sampled-input/uniform descriptor and target pass for one fullscreen effect. */
    default FullscreenEffectBinding requireFullscreenEffectBinding(FullscreenEffectRequest request,
                                                                   RenderExecution execution) {
        throw new UnsupportedOperationException("fullscreen effects are not configured by this resource service");
    }
}
