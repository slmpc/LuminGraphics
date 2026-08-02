package com.github.slmpc.lumingraphics.render.resource;

import com.github.slmpc.lumingraphics.core.vertex.VertexSchema;
import com.github.slmpc.lumingraphics.render.pipeline.LuminPipelineCatalog;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipelineCreateInfo;

/** 将 Lumin catalog 顶点 ABI 映射为 Prism 管线声明。 */
final class DefaultVertexLayouts {
    private DefaultVertexLayouts() { }

    static void apply(RhiGraphicsPipelineCreateInfo.Builder builder,
                      LuminPipelineCatalog.VertexLayout layout) {
        if (layout == LuminPipelineCatalog.VertexLayout.FULLSCREEN) return;
        if (layout == LuminPipelineCatalog.VertexLayout.POSITION) {
            builder.vertexBinding(0, 12).vertexAttribute(0, 0, RhiFormat.RGB32_FLOAT, 0);
            return;
        }
        if (layout == LuminPipelineCatalog.VertexLayout.POSITION_COLOR) {
            builder.vertexBinding(0, 16).vertexAttribute(0, 0, RhiFormat.RGB32_FLOAT, 0)
                    .vertexAttribute(1, 0, RhiFormat.RGBA8_UNORM, 12);
            return;
        }
        if (layout == LuminPipelineCatalog.VertexLayout.POSITION_UV_COLOR) {
            builder.vertexBinding(0, 24).vertexAttribute(0, 0, RhiFormat.RGB32_FLOAT, 0)
                    .vertexAttribute(1, 0, RhiFormat.RG32_FLOAT, 12)
                    .vertexAttribute(2, 0, RhiFormat.RGBA8_UNORM, 20);
            return;
        }
        VertexSchema schema = layout.schema();
        builder.vertexBinding(0, schema.stride());
        schema.elements().forEach(element -> builder.vertexAttribute(
                element.location(), 0, element.format(), element.offset()));
    }
}
