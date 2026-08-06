package com.github.slmpc.lumingraphics.render.pipeline;

/**
 * Stable public ids used by injected pipeline resolvers.
 */
public final class LuminRenderPipelines {
    public static final String RECTANGLE = "rectangle";
    public static final String ROUND_RECTANGLE = "round-rectangle";
    public static final String ROUND_RECTANGLE_OUTLINE = "round-rectangle-outline";
    public static final String SHADOW = "shadow";
    public static final String SEGMENTED_SHADOW = "segmented-shadow";
    public static final String TEXTURE = "texture";
    public static final String TRIANGLE = "triangle";
    public static final String TTF_FONT_AA = "ttf-font-aa";

    private LuminRenderPipelines() {
    }

    public static LuminPipelineCatalog.PipelineDescriptor require(String id) {
        return LuminPipelineCatalog.require(id);
    }
}
