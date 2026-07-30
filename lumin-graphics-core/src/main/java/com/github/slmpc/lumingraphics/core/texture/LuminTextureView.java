package com.github.slmpc.lumingraphics.core.texture;

import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;
import com.github.slmpc.lumingraphics.core.resource.ManagedResource;
import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiNativeObject;
import com.github.slmpc.prismrhi.resource.RhiNativeObjectType;

import java.util.Optional;

public final class LuminTextureView {
    private final ManagedResource<RhiImageView> view;

    public LuminTextureView(ManagedResource<RhiImageView> view) {
        if (view == null) {
            throw new LuminValidationException("texture view must not be null");
        }
        this.view = view;
    }

    public RhiContextIdentity contextIdentity() {
        return view.contextIdentity();
    }

    public RhiImageView rhiView(RhiContextIdentity contextIdentity) {
        return view.get(contextIdentity);
    }

    public Optional<RhiNativeObject> nativeObject(
            RhiContextIdentity contextIdentity, RhiNativeObjectType type
    ) {
        if (type == null) {
            throw new LuminValidationException("native object type must not be null");
        }
        return rhiView(contextIdentity).getNativeObject(type);
    }
}
