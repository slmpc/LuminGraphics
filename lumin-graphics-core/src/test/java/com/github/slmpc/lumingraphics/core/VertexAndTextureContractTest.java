package com.github.slmpc.lumingraphics.core;

import com.github.slmpc.lumingraphics.core.exception.LuminContextMismatchException;
import com.github.slmpc.lumingraphics.core.exception.LuminResourceInvalidatedException;
import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;
import com.github.slmpc.lumingraphics.core.resource.ManagedResource;
import com.github.slmpc.lumingraphics.core.resource.ResourceRegistry;
import com.github.slmpc.lumingraphics.core.texture.LuminTexture;
import com.github.slmpc.lumingraphics.core.vertex.LuminVertexFormats;
import com.github.slmpc.lumingraphics.testkit.RecordingRhiDevice;
import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.context.RhiInvalidationToken;
import com.github.slmpc.prismrhi.resource.RhiImage;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiOwnership;
import com.github.slmpc.prismrhi.resource.RhiSampler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VertexAndTextureContractTest {
    @Test
    void vertexSchemasMapDeterministicallyWithoutNativeAllocation() {
        assertEquals(48, LuminVertexFormats.ROUND_RECT.stride());
        assertEquals(4, LuminVertexFormats.ROUND_RECT.toRhiAttributes(0).size());
        assertEquals(52, LuminVertexFormats.ROUND_RECT_OUTLINE.stride());
        assertEquals(56, LuminVertexFormats.TEXTURE.stride());
        assertEquals(0, LuminVertexFormats.TEXTURE.toRhiBinding(0).binding());
        assertThrows(LuminValidationException.class, () -> LuminVertexFormats.TEXTURE.toRhiBinding(-1));
    }

    @Test
    void textureViewEnforcesContextAndSharedInvalidation() {
        RecordingRhiDevice fake = new RecordingRhiDevice(30, "texture");
        ResourceRegistry registry = new ResourceRegistry(fake.contextIdentity());
        RhiInvalidationToken token = new RhiInvalidationToken();
        ManagedResource<RhiImage> image = registry.register(
                "image", fake.resource(RhiImage.class, "image", RhiOwnership.OWNED, token),
                RhiOwnership.OWNED, token
        );
        ManagedResource<RhiImageView> view = registry.register(
                "view", fake.resource(RhiImageView.class, "view", RhiOwnership.OWNED, token),
                RhiOwnership.OWNED, token
        );
        ManagedResource<RhiSampler> sampler = registry.register(
                "sampler", fake.resource(RhiSampler.class, "sampler", RhiOwnership.OWNED, token),
                RhiOwnership.OWNED, token
        );
        LuminTexture texture = new LuminTexture(image, view, sampler);

        assertEquals(view.get(fake.contextIdentity()), texture.view().rhiView(fake.contextIdentity()));
        assertThrows(LuminContextMismatchException.class,
                () -> texture.image(new RhiContextIdentity(31, "wrong")));
        token.invalidate();
        assertThrows(LuminResourceInvalidatedException.class,
                () -> texture.view().rhiView(fake.contextIdentity()));
        texture.close();
        texture.close();
        assertEquals(1, fake.deleterCount("image"));
        assertEquals(1, fake.deleterCount("view"));
        assertEquals(1, fake.deleterCount("sampler"));
    }
}
