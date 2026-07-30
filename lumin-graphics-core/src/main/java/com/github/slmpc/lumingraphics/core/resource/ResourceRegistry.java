package com.github.slmpc.lumingraphics.core.resource;

import com.github.slmpc.lumingraphics.core.exception.LuminCleanupException;
import com.github.slmpc.lumingraphics.core.exception.LuminResourceClosedException;
import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;
import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.context.RhiInvalidationToken;
import com.github.slmpc.prismrhi.resource.RhiOwnership;
import com.github.slmpc.prismrhi.resource.RhiResource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResourceRegistry implements AutoCloseable {
    private final RhiContextIdentity contextIdentity;
    private final Map<String, ManagedResource<?>> resources = new LinkedHashMap<>();
    private boolean closed;

    public ResourceRegistry(RhiContextIdentity contextIdentity) {
        if (contextIdentity == null) {
            throw new LuminValidationException("resource registry context must not be null");
        }
        this.contextIdentity = contextIdentity;
    }

    public synchronized <T extends RhiResource> ManagedResource<T> register(
            String name, T resource, RhiOwnership ownership, RhiInvalidationToken invalidationToken
    ) {
        requireOpen();
        if (name == null || name.isBlank()) {
            throw new LuminValidationException("resource name must not be blank");
        }
        String normalizedName = name.trim();
        if (resources.containsKey(normalizedName)) {
            throw new LuminValidationException("resource name is already registered: " + normalizedName);
        }
        ManagedResource<T> managed = new ManagedResource<>(
                normalizedName, resource, ownership, contextIdentity, invalidationToken
        );
        resources.put(managed.name(), managed);
        return managed;
    }

    public synchronized int size() {
        return resources.size();
    }

    public synchronized void invalidateAll() {
        requireOpen();
        resources.values().forEach(ManagedResource::invalidate);
    }

    private void requireOpen() {
        if (closed) {
            throw new LuminResourceClosedException("resource registry is closed");
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        List<ManagedResource<?>> reverse = new ArrayList<>(resources.values());
        LuminCleanupException failure = null;
        for (int index = reverse.size() - 1; index >= 0; index--) {
            try {
                reverse.get(index).close();
            } catch (RuntimeException closeFailure) {
                if (failure == null) {
                    failure = new LuminCleanupException("one or more resources failed to close");
                }
                failure.addSuppressed(closeFailure);
            }
        }
        resources.clear();
        if (failure != null) {
            throw failure;
        }
    }
}
