package com.github.slmpc.lumingraphics.core;

import com.github.slmpc.lumingraphics.core.exception.LuminCleanupException;
import com.github.slmpc.lumingraphics.core.exception.LuminContextMismatchException;
import com.github.slmpc.lumingraphics.core.exception.LuminResourceClosedException;
import com.github.slmpc.lumingraphics.core.exception.LuminResourceInvalidatedException;
import com.github.slmpc.lumingraphics.core.resource.ManagedResource;
import com.github.slmpc.lumingraphics.core.resource.ResourceRegistry;
import com.github.slmpc.lumingraphics.testkit.RecordingRhiDevice;
import com.github.slmpc.prismrhi.backend.BackendApi;
import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.context.RhiInvalidationToken;
import com.github.slmpc.prismrhi.resource.RhiOwnership;
import com.github.slmpc.prismrhi.resource.RhiResource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceLifecycleTest {
    @Test
    void ownedClosesOnceBorrowedNeverClosesAndRegistryUsesReverseOrder() {
        RecordingRhiDevice fake = new RecordingRhiDevice(1, "lifecycle");
        ResourceRegistry registry = new ResourceRegistry(fake.contextIdentity());
        ManagedResource<RhiResource> first = registry.register(
                "first", fake.resource(RhiResource.class, "first", RhiOwnership.OWNED),
                RhiOwnership.OWNED, new RhiInvalidationToken()
        );
        registry.register(
                "borrowed", fake.resource(RhiResource.class, "borrowed", RhiOwnership.BORROWED),
                RhiOwnership.BORROWED, new RhiInvalidationToken()
        );
        registry.register(
                "last", fake.resource(RhiResource.class, "last", RhiOwnership.OWNED),
                RhiOwnership.OWNED, new RhiInvalidationToken()
        );

        registry.close();
        registry.close();
        first.close();

        assertEquals(1, fake.deleterCount("first"));
        assertEquals(0, fake.deleterCount("borrowed"));
        assertEquals(1, fake.deleterCount("last"));
        assertEquals(List.of(
                "resource.create name=first ownership=OWNED native=101",
                "resource.create name=borrowed ownership=BORROWED native=102",
                "resource.create name=last ownership=OWNED native=103",
                "resource.close name=last ownership=OWNED deleter=1",
                "resource.close name=first ownership=OWNED deleter=1"
        ), fake.trace());
    }

    @Test
    void invalidationCloseAndWrongContextHaveTypedOutcomes() {
        RecordingRhiDevice fake = new RecordingRhiDevice(4, "state");
        ResourceRegistry registry = new ResourceRegistry(fake.contextIdentity());
        RhiInvalidationToken token = new RhiInvalidationToken();
        ManagedResource<RhiResource> resource = registry.register(
                "stateful", fake.resource(RhiResource.class, "stateful", RhiOwnership.OWNED, token),
                RhiOwnership.OWNED, token
        );

        assertThrows(LuminContextMismatchException.class,
                () -> resource.get(new RhiContextIdentity(5, "other")));
        token.invalidate();
        assertThrows(LuminResourceInvalidatedException.class,
                () -> resource.get(fake.contextIdentity()));
        resource.close();
        assertThrows(LuminResourceClosedException.class,
                () -> resource.get(fake.contextIdentity()));

        RhiInvalidationToken closedToken = new RhiInvalidationToken();
        ManagedResource<RhiResource> tokenClosed = new ResourceRegistry(fake.contextIdentity()).register(
                "token-closed", fake.resource(RhiResource.class, "token-closed", RhiOwnership.BORROWED),
                RhiOwnership.BORROWED, closedToken
        );
        closedToken.close();
        assertThrows(LuminResourceClosedException.class,
                () -> tokenClosed.get(fake.contextIdentity()));
    }

    @Test
    void cleanupContinuesAndAggregatesFailures() {
        List<String> closes = new ArrayList<>();
        ResourceRegistry registry = new ResourceRegistry(new RhiContextIdentity(8, "cleanup"));
        registry.register("first", closingResource("first", closes, true), RhiOwnership.OWNED, new RhiInvalidationToken());
        registry.register("second", closingResource("second", closes, false), RhiOwnership.OWNED, new RhiInvalidationToken());
        registry.register("third", closingResource("third", closes, true), RhiOwnership.OWNED, new RhiInvalidationToken());

        LuminCleanupException failure = assertThrows(LuminCleanupException.class, registry::close);
        assertEquals(List.of("third", "second", "first"), closes);
        assertEquals(2, failure.getSuppressed().length);
    }

    private static RhiResource closingResource(String name, List<String> closes, boolean fail) {
        return new RhiResource() {
            @Override
            public BackendApi api() {
                return BackendApi.VULKAN;
            }

            @Override
            public void close() {
                closes.add(name);
                if (fail) {
                    throw new IllegalStateException(name);
                }
            }
        };
    }
}
