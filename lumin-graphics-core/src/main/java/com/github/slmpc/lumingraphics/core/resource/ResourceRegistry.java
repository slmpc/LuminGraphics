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

/**
 * 以名称管理同一 Prism 上下文内资源的所有权和失效状态。
 *
 * <p>资源名称在注册表内唯一。关闭时按注册的逆序释放资源，从而先释放后创建的依赖对象。
 * 注册表本身不负责创建 Prism 资源。</p>
 */
public final class ResourceRegistry implements AutoCloseable {
    private final RhiContextIdentity contextIdentity;
    private final Map<String, ManagedResource<?>> resources = new LinkedHashMap<>();
    private boolean closed;

    /**
     * 创建与给定 Prism 上下文绑定的注册表。
     *
     * @param contextIdentity 所有登记资源必须匹配的上下文标识
     */
    public ResourceRegistry(RhiContextIdentity contextIdentity) {
        if (contextIdentity == null) {
            throw new LuminValidationException("resource registry context must not be null");
        }
        this.contextIdentity = contextIdentity;
    }

    /**
     * 登记一个资源。
     *
     * @param name              调试和查找用的非空白唯一名称
     * @param resource          调用方已经创建的 Prism 资源
     * @param ownership         资源关闭职责
     * @param invalidationToken 与资源上下文关联的失效令牌
     * @param <T>               已登记 Prism 资源的具体类型
     * @return 包含所有权和有效性检查的资源包装器
     */
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

    /**
     * 返回当前登记资源数。
     *
     * @return 已登记资源数
     */
    public synchronized int size() {
        return resources.size();
    }

    /**
     * 标记全部已登记资源为失效，但不关闭它们。
     */
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
