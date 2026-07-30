package com.github.slmpc.lumingraphics.core.vertex;

import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;
import com.github.slmpc.prismrhi.pipeline.RhiVertexAttribute;
import com.github.slmpc.prismrhi.pipeline.RhiVertexInputBinding;
import com.github.slmpc.prismrhi.pipeline.RhiVertexInputRate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record VertexSchema(int stride, List<VertexElement> elements) {
    public VertexSchema {
        if (stride <= 0 || elements == null || elements.isEmpty()) {
            throw new LuminValidationException("vertex schema requires a positive stride and elements");
        }
        Set<Integer> locations = new HashSet<>();
        for (VertexElement element : elements) {
            if (element == null || !locations.add(element.location())) {
                throw new LuminValidationException("vertex schema elements and locations must be unique");
            }
            long end = (long) element.offset() + element.format().bytesPerPixel();
            if (end > stride) {
                throw new LuminValidationException("vertex element exceeds schema stride");
            }
        }
        elements = List.copyOf(elements);
    }

    public RhiVertexInputBinding toRhiBinding(int binding) {
        if (binding < 0) {
            throw new LuminValidationException("vertex binding must not be negative");
        }
        return new RhiVertexInputBinding(binding, stride, RhiVertexInputRate.VERTEX);
    }

    public List<RhiVertexAttribute> toRhiAttributes(int binding) {
        return elements.stream().map(element -> element.toRhi(binding)).toList();
    }
}
