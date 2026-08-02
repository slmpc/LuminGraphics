package com.github.slmpc.lumingraphics.render;

import com.github.slmpc.lumingraphics.render.pipeline.LuminPipelineCatalog;
import com.github.slmpc.lumingraphics.render.resource.DefaultRenderResources;
import com.github.slmpc.lumingraphics.render.resource.RenderResourceException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DefaultRenderResourcesApiTest {
    @Test
    void frameUniformAbiAndPipelineIdsRemainGenericAndCatalogAligned() {
        assertEquals(80, DefaultRenderResources.FRAME_UNIFORM_BYTES);
        assertEquals(LuminPipelineCatalog.entries().stream()
                        .map(LuminPipelineCatalog.PipelineDescriptor::id).toList(),
                DefaultRenderResources.demandedPipelineIds());
        assertThrows(UnsupportedOperationException.class,
                () -> DefaultRenderResources.demandedPipelineIds().add("unexpected"));
    }

    @Test
    void resourceExceptionPreservesItsStableErrorCodeAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("cause");
        RenderResourceException failure = new RenderResourceException(
                RenderResourceException.Code.MISSING_DESCRIPTOR, "missing", cause);

        assertEquals(RenderResourceException.Code.MISSING_DESCRIPTOR, failure.code());
        assertSame(cause, failure.getCause());
    }
}
