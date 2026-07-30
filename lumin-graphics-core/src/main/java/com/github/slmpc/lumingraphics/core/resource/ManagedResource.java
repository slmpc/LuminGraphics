package com.github.slmpc.lumingraphics.core.resource;

import com.github.slmpc.lumingraphics.core.exception.LuminContextMismatchException;
import com.github.slmpc.lumingraphics.core.exception.LuminResourceClosedException;
import com.github.slmpc.lumingraphics.core.exception.LuminResourceInvalidatedException;
import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;
import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.context.RhiInvalidationToken;
import com.github.slmpc.prismrhi.RhiResourceClosedException;
import com.github.slmpc.prismrhi.RhiResourceInvalidatedException;
import com.github.slmpc.prismrhi.resource.RhiOwnership;
import com.github.slmpc.prismrhi.resource.RhiResource;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ManagedResource<T extends RhiResource> implements AutoCloseable {
    private final String name;
    private final T resource;
    private final RhiOwnership ownership;
    private final RhiContextIdentity contextIdentity;
    private final RhiInvalidationToken invalidationToken;
    private final AtomicBoolean closed = new AtomicBoolean();

    ManagedResource(
            String name, T resource, RhiOwnership ownership,
            RhiContextIdentity contextIdentity, RhiInvalidationToken invalidationToken
    ) {
        if (name == null || name.isBlank() || resource == null || ownership == null
                || contextIdentity == null || invalidationToken == null) {
            throw new LuminValidationException("managed resource values must not be null or blank");
        }
        this.name = name;
        this.resource = resource;
        this.ownership = ownership;
        this.contextIdentity = contextIdentity;
        this.invalidationToken = invalidationToken;
    }

    public String name() {
        return name;
    }

    public RhiOwnership ownership() {
        return ownership;
    }

    public RhiContextIdentity contextIdentity() {
        return contextIdentity;
    }

    public T get(RhiContextIdentity requestingContext) {
        if (closed.get()) {
            throw new LuminResourceClosedException(name + " is closed");
        }
        try {
            contextIdentity.requireSameContext(requestingContext);
        } catch (RuntimeException mismatch) {
            throw new LuminContextMismatchException(name + " belongs to another RHI context", mismatch);
        }
        try {
            invalidationToken.requireValid();
        } catch (RhiResourceClosedException tokenClosed) {
            throw new LuminResourceClosedException(name + " invalidation token is closed", tokenClosed);
        } catch (RhiResourceInvalidatedException invalid) {
            throw new LuminResourceInvalidatedException(name + " is invalidated", invalid);
        }
        return resource;
    }

    public boolean isClosed() {
        return closed.get();
    }

    public boolean invalidate() {
        if (closed.get()) {
            throw new LuminResourceClosedException(name + " is closed");
        }
        return invalidationToken.invalidate();
    }

    @Override
    public void close() {
        // Mark first: a throwing native deleter is unsafe to retry and risk a double free.
        if (closed.compareAndSet(false, true) && ownership == RhiOwnership.OWNED) {
            resource.close();
        }
    }
}
