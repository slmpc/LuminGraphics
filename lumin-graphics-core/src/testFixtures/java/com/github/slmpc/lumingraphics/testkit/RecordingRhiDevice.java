package com.github.slmpc.lumingraphics.testkit;

import com.github.slmpc.prismrhi.backend.BackendApi;
import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.context.RhiInvalidationToken;
import com.github.slmpc.prismrhi.command.RhiCommandPool;
import com.github.slmpc.prismrhi.command.RhiCommandPoolCreateInfo;
import com.github.slmpc.prismrhi.device.RhiDevice;
import com.github.slmpc.prismrhi.resource.RhiNativeObject;
import com.github.slmpc.prismrhi.resource.RhiNativeObjectType;
import com.github.slmpc.prismrhi.resource.RhiOwnership;
import com.github.slmpc.prismrhi.resource.RhiResource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class RecordingRhiDevice {
    private final Object lock = new Object();
    private final RhiContextIdentity contextIdentity;
    private final List<String> trace = new ArrayList<>();
    private final Map<String, AtomicInteger> deleterCounts = new LinkedHashMap<>();
    private final AtomicLong nextNativeId = new AtomicLong(100);
    private final RhiDevice device;

    public RecordingRhiDevice(long contextId, String name) {
        contextIdentity = new RhiContextIdentity(contextId, name);
        device = proxy(RhiDevice.class, this::invokeDevice);
    }

    public RhiDevice device() {
        return device;
    }

    public RhiContextIdentity contextIdentity() {
        return contextIdentity;
    }

    public List<String> trace() {
        synchronized (lock) {
            return List.copyOf(trace);
        }
    }

    public int deleterCount(String name) {
        synchronized (lock) {
            AtomicInteger count = deleterCounts.get(name);
            return count == null ? 0 : count.get();
        }
    }

    public <T extends RhiResource> T resource(Class<T> type, String name, RhiOwnership ownership) {
        return resource(type, name, ownership, new RhiInvalidationToken());
    }

    public <T extends RhiResource> T resource(
            Class<T> type, String name, RhiOwnership ownership, RhiInvalidationToken invalidation
    ) {
        if (type == null || !type.isInterface() || name == null || name.isBlank()
                || ownership == null || invalidation == null) {
            throw new IllegalArgumentException("fake resource inputs must be non-null and valid");
        }
        long nativeId = nextNativeId.incrementAndGet();
        synchronized (lock) {
            if (deleterCounts.containsKey(name)) {
                throw new IllegalArgumentException("fake resource name already exists: " + name);
            }
            deleterCounts.put(name, new AtomicInteger());
            trace.add("resource.create name=" + name + " ownership=" + ownership + " native=" + nativeId);
        }
        AtomicBoolean closed = new AtomicBoolean();
        return proxy(type, (proxy, method, arguments) -> invokeResource(
                proxy, method, arguments, name, ownership, invalidation, nativeId, closed
        ));
    }

    private Object invokeDevice(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "api" -> BackendApi.VULKAN;
            case "contextIdentity" -> contextIdentity;
            case "createCommandPool" -> RecordingCommandHandlers.commandPool(
                    (RhiCommandPoolCreateInfo) arguments[0], this::record
            );
            case "waitIdle" -> record("device.waitIdle");
            case "close" -> record("device.close");
            case "toString" -> "RecordingRhiDevice[" + contextIdentity.diagnosticName() + "]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> throw unsupported("RhiDevice", method);
        };
    }

    private Object invokeResource(
            Object proxy, Method method, Object[] arguments, String name, RhiOwnership ownership,
            RhiInvalidationToken invalidation, long nativeId, AtomicBoolean closed
    ) {
        return switch (method.getName()) {
            case "api" -> BackendApi.VULKAN;
            case "close" -> closeResource(name, ownership, closed);
            case "getNativeObject" -> nativeObject(arguments, invalidation, nativeId, closed, name);
            case "toString" -> "RecordingRhiResource[" + name + "]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> throw unsupported(proxy.getClass().getInterfaces()[0].getSimpleName(), method);
        };
    }

    private Object nativeObject(
            Object[] arguments, RhiInvalidationToken invalidation, long nativeId,
            AtomicBoolean closed, String name
    ) {
        if (closed.get()) {
            throw new IllegalStateException("fake resource is closed: " + name);
        }
        invalidation.requireValid();
        RhiNativeObjectType type = (RhiNativeObjectType) arguments[0];
        return Optional.of(new RhiNativeObject(type, nativeId));
    }

    private Object closeResource(String name, RhiOwnership ownership, AtomicBoolean closed) {
        if (closed.compareAndSet(false, true)) {
            int deletes = ownership == RhiOwnership.OWNED
                    ? deleterCounts.get(name).incrementAndGet()
                    : deleterCounts.get(name).get();
            record("resource.close name=" + name + " ownership=" + ownership + " deleter=" + deletes);
        }
        return null;
    }

    private Object record(String event) {
        synchronized (lock) {
            trace.add(event);
        }
        return null;
    }

    static UnsupportedRhiCallException unsupported(String receiver, Method method) {
        return new UnsupportedRhiCallException(
                "unsupported fake RHI call: " + receiver + "." + method.getName()
        );
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
        return type.cast(proxy);
    }
}
