package com.github.slmpc.lumingraphics.core.texture;

import com.github.slmpc.lumingraphics.core.exception.LuminCleanupException;
import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;
import com.github.slmpc.lumingraphics.core.resource.ManagedResource;
import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.resource.RhiImage;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiSampler;

public final class LuminTexture implements AutoCloseable {
    private final ManagedResource<RhiImage> image;
    private final ManagedResource<RhiImageView> view;
    private final ManagedResource<RhiSampler> sampler;

    public LuminTexture(
            ManagedResource<RhiImage> image,
            ManagedResource<RhiImageView> view,
            ManagedResource<RhiSampler> sampler
    ) {
        if (image == null || view == null || sampler == null) {
            throw new LuminValidationException("texture resources must not be null");
        }
        RhiContextIdentity context = image.contextIdentity();
        requireSame(context, view.contextIdentity());
        requireSame(context, sampler.contextIdentity());
        this.image = image;
        this.view = view;
        this.sampler = sampler;
    }

    public RhiContextIdentity contextIdentity() {
        return image.contextIdentity();
    }

    public RhiImage image(RhiContextIdentity context) {
        return image.get(context);
    }

    public LuminTextureView view() {
        return new LuminTextureView(view);
    }

    public RhiSampler sampler(RhiContextIdentity context) {
        return sampler.get(context);
    }

    private static void requireSame(RhiContextIdentity expected, RhiContextIdentity actual) {
        try {
            expected.requireSameContext(actual);
        } catch (RuntimeException mismatch) {
            throw new LuminValidationException("texture resources must belong to the same RHI context");
        }
    }

    @Override
    public void close() {
        LuminCleanupException failure = null;
        for (AutoCloseable resource : new AutoCloseable[]{sampler, view, image}) {
            try {
                resource.close();
            } catch (Exception closeFailure) {
                if (failure == null) {
                    failure = new LuminCleanupException("one or more texture resources failed to close");
                }
                failure.addSuppressed(closeFailure);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
