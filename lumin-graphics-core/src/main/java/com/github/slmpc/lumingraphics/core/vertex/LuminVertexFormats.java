package com.github.slmpc.lumingraphics.core.vertex;

import com.github.slmpc.prismrhi.format.RhiFormat;

import java.util.List;

public final class LuminVertexFormats {
    public static final VertexSchema ROUND_RECT = new VertexSchema(48, List.of(
            new VertexElement(VertexSemantic.POSITION, 0, RhiFormat.RGB32_FLOAT, 0),
            new VertexElement(VertexSemantic.COLOR, 1, RhiFormat.RGBA8_UNORM, 12),
            new VertexElement(VertexSemantic.INNER_RECT, 2, RhiFormat.RGBA32_FLOAT, 16),
            new VertexElement(VertexSemantic.RADIUS, 3, RhiFormat.RGBA32_FLOAT, 32)
    ));

    public static final VertexSchema ROUND_RECT_OUTLINE = new VertexSchema(52, List.of(
            new VertexElement(VertexSemantic.POSITION, 0, RhiFormat.RGB32_FLOAT, 0),
            new VertexElement(VertexSemantic.COLOR, 1, RhiFormat.RGBA8_UNORM, 12),
            new VertexElement(VertexSemantic.INNER_RECT, 2, RhiFormat.RGBA32_FLOAT, 16),
            new VertexElement(VertexSemantic.RADIUS, 3, RhiFormat.RGBA32_FLOAT, 32),
            new VertexElement(VertexSemantic.OUTLINE_WIDTH, 4, RhiFormat.R32_FLOAT, 48)
    ));

    public static final VertexSchema TEXTURE = new VertexSchema(56, List.of(
            new VertexElement(VertexSemantic.POSITION, 0, RhiFormat.RGB32_FLOAT, 0),
            new VertexElement(VertexSemantic.COLOR, 1, RhiFormat.RGBA8_UNORM, 12),
            new VertexElement(VertexSemantic.UV, 2, RhiFormat.RG32_FLOAT, 16),
            new VertexElement(VertexSemantic.INNER_RECT, 3, RhiFormat.RGBA32_FLOAT, 24),
            new VertexElement(VertexSemantic.RADIUS, 4, RhiFormat.RGBA32_FLOAT, 40)
    ));

    private LuminVertexFormats() {
    }
}
