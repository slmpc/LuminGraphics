package com.github.slmpc.lumingraphics.render.scheduler;

import com.github.slmpc.lumingraphics.core.texture.LuminTexture;

public sealed interface Render2DTexture permits Render2DTexture.Resource, Render2DTexture.Lumin {
    record Resource(String id) implements Render2DTexture {
        public Resource {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("texture id is blank");
        }
    }

    record Lumin(LuminTexture texture) implements Render2DTexture {
        public Lumin {
            if (texture == null) throw new IllegalArgumentException("texture is null");
        }
    }
}
