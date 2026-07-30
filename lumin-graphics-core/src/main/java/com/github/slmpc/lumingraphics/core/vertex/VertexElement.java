package com.github.slmpc.lumingraphics.core.vertex;

import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.pipeline.RhiVertexAttribute;

public record VertexElement(VertexSemantic semantic, int location, RhiFormat format, int offset) {
    public VertexElement {
        if (semantic == null || format == null) {
            throw new LuminValidationException("vertex element values must not be null");
        }
        if (location < 0 || offset < 0 || format == RhiFormat.UNDEFINED) {
            throw new LuminValidationException("vertex element location, offset, and format must be valid");
        }
    }

    public RhiVertexAttribute toRhi(int binding) {
        if (binding < 0) {
            throw new LuminValidationException("vertex binding must not be negative");
        }
        return new RhiVertexAttribute(location, binding, format, offset);
    }
}
